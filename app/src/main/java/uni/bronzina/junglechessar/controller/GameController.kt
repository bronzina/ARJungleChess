package uni.bronzina.junglechessar.controller

import android.util.Log.d
import android.util.Log.e
import uni.bronzina.junglechessar.model.*
import uni.bronzina.junglechessar.storage.ChessStorageManager

class GameController {
    private val TAG = GameController::class.java.simpleName
    private val mStorageManager = ChessStorageManager()
    private var mCurrentGameState = GameState.NO_WIN_USER
    private var mRoomId = 0
    private var mCurrentUser: ChessUserInfo? = null
    private var mOtherUser: ChessUserInfo? = null
    private var mIsGameStarted = false
    private var mCurrentRound = 0

    private lateinit var onGameFinish: (gameState: GameState, currentRound: Int) -> Unit
    private lateinit var onUserTurn: (userAturn: Boolean) -> Unit
    //private lateinit var onTimeStart: (beginTimer: Long) -> Unit
    private lateinit var onTimeStartA: (beginTimer: Long) -> Unit
    private lateinit var onTimeStartB: (beginTimer: Long) -> Unit
    //private lateinit var onTimeEnd: (endTimer: Long) -> Unit
    private lateinit var onTimeEndA: (perf: PerformanceModel) -> Unit
    private lateinit var onTimeEndB: (perf: PerformanceModel) -> Unit

    companion object {
        val instance: GameController = GameController()
    }

    //FOR init game, User A needs to store
    fun initGame(cloudAnchorId: String, onInitGame: (roomId: String?) -> Unit) {

        mStorageManager.nextRoomId { roomId ->
            if (roomId == null) {
                e(TAG, "Could not obtain a short code.")
                onInitGame(null)
            } else {
                mRoomId = roomId
                mStorageManager.writeCloudAnchorIdUsingRoomId(roomId, cloudAnchorId)
                d(TAG, "Anchor hosted stored shortCode: $roomId" +
                        " CloudId: $cloudAnchorId")
                onInitGame(roomId.toString())
            }
        }
    }

    //USER B needs to pairGame with a valid roomId
    fun pairGame(roomId: Int, onPairGame: (cloudAnchorId: String?) -> Unit) {
        mStorageManager.readCloudAnchorId(roomId) { cloudAnchorId ->
            mRoomId = roomId
            if (cloudAnchorId == null) {
                e(TAG, "Could not obtain a cloudAnchorId.")
                onPairGame(null)
            } else {
                d(TAG, "Obtain cloudAnchorId success" +
                        " CloudId: $cloudAnchorId")
                onPairGame(cloudAnchorId)
            }
        }
    }

    /**
     *  Both UserA and UserB confirm GameStart then UI will receive the onGameStart callback
     *  Start UserA round first
     */
    fun confirmGameStart(onGameStart: (isUserAConfirm: Boolean, isUserBConfirm: Boolean) -> Unit) {
        d(TAG, "confirmGameStart")

        if (mCurrentUser == null) {
            return
        }

        val isCurrentUserA = mCurrentUser!!.userType == UserType.USER_A

        mStorageManager.writeGameStart(mRoomId, isCurrentUserA)
        mStorageManager.readGameStart(mRoomId) { isUserAReady, isUserBReady ->
            d(TAG, "confirmGameStart: isUserAReady: $isUserAReady, isUserBReady: $isUserBReady")

            if ((isCurrentUserA && isUserBReady) || (!isCurrentUserA && isUserAReady)) {
                if (!mIsGameStarted) {
                    d(TAG, "GameStart, mark current game state to USER_A_TURN, start to listen animal update")
                    mCurrentGameState = GameState.USER_A_TURN
                    mIsGameStarted = true
                }
            }

            if (isUserAReady && isUserBReady) {
                d(TAG, "isUserAReady, isUserBReady, start game, now UserA turn")
                onGameStart(isUserAReady, isUserBReady)
            }
        }
    }

    fun test() {
        mStorageManager.readGameStart(11) { isUserAReady, isUserBReady ->
            d(TAG, "confirmGameStart: isUserAReady: $isUserAReady, isUserBReady: $isUserBReady")
        }
    }

    /**
     *  Every User finish his turn, call updateGameInfo, other User will receive onUserTurn callback
     *  then UI needs to redraw and start another round
     */
    fun updateGameInfo(updatedAnimal1: Animal, updatedAnimal2: Animal) {
        d(TAG, "updateGameInfo, user: ${updatedAnimal1.animalDrawType}, animal1: ${updatedAnimal1.animalType}, animal2: ${updatedAnimal2.animalType}")
        mCurrentRound++

        mStorageManager.writeAnimalInfo(mRoomId, updatedAnimal1, updatedAnimal2, updatedAnimal1.animalDrawType)

    }

    /*fun initGameBoard(animalList: List<Animal>) {
    }*/

    fun updateTurnInfo(userATurn: Boolean){
        d(TAG, "updateTurnInfo")
        mStorageManager.writeTurnInfo(mRoomId, userATurn)
    }

    fun setOnUserTurnListener(onUserTurn: (userATurn: Boolean) -> Unit){
        d(TAG, "setOnUserTurn")
        this.onUserTurn = onUserTurn
        mStorageManager.readTurnInfo(mRoomId) { userATurn ->
            onUserTurn(userATurn)
        }
    }

    fun updateGameFinish(userAWin: Boolean){
        d(TAG, "updateGameFinish")
        mStorageManager.writeGameFinish(mRoomId, userAWin)
    }

    fun setOnGameFinishListener(onGameFinish: (gameState: GameState, currentRound: Int) -> Unit) {
        d(TAG, "setOnGameFinishListener")
        this.onGameFinish = onGameFinish

        mStorageManager.readGameFinish(mRoomId) { userAWin ->
            if(userAWin){
                onGameFinish(GameState.USER_A_WIN, mCurrentRound)
            }
            else{
                onGameFinish(GameState.USER_B_WIN, mCurrentRound)
            }
        }
    }

    fun storeUserInfo(isUserA: Boolean, uid: String, displayName: String?, photoUrl: String?) {
        d(TAG, "storeUserInfo")
        val userInfo = ChessUserInfo(uid)
        displayName?.let {
            userInfo.displayName = it
        }
        photoUrl?.let {
            userInfo.photoUrl = it
        }
        userInfo.userType = if (isUserA) UserType.USER_A else UserType.USER_B
        mCurrentUser = userInfo
        mStorageManager.writeUserInfo(mRoomId, userInfo)
    }

    fun getUserInfo(isNeedUserA: Boolean, onReadUserInfo: (currentUserInfo: ChessUserInfo, otherUserInfo: ChessUserInfo) -> Unit) {
        d(TAG, "getUserInfo: $isNeedUserA")
        if (mCurrentUser == null) {
            e(TAG, "getUserInfo, init current user First")
            return
        }

        mStorageManager.readUserInfo(mRoomId, isNeedUserA) { chessUserInfo ->
            d(TAG, "onReadUserInfo: $chessUserInfo")
            if (isNeedUserA && chessUserInfo.userType == UserType.USER_B || ((!isNeedUserA) && chessUserInfo.userType == UserType.USER_A)) {
                e(TAG, "onReadUserInfo data is not needed, no need to notify UI")
            } else {
                mOtherUser = chessUserInfo
                if (mCurrentUser != null && mOtherUser != null)
                    onReadUserInfo(mCurrentUser!!, mOtherUser!!)
                else
                    e(TAG, "currentUser is null or otherUser is null")
            }
        }
    }

    fun getAnimalInfoMove(isNeedUserA: Boolean, onReadAnimalInfoMove: (animal1: Animal) -> Unit) {
        d(TAG, "getAnimalInfoMove")

        mStorageManager.readAnimalInfoMove(mRoomId, isNeedUserA) { animal ->
            d(TAG, "onReadAnimalInfoMove")
            onReadAnimalInfoMove(animal)
        }
    }

    fun getAnimalInfoEat(isNeedUserA: Boolean, onReadAnimalInfoEat: (animal2: Animal) -> Unit) {
        d(TAG, "getAnimalInfoEat")

        mStorageManager.readAnimalInfoEat(mRoomId, isNeedUserA) { animal ->
            d(TAG, "onReadAnimalInfoEat")
            onReadAnimalInfoEat(animal)
        }
    }

    /*fun storeUpdateTimeStart(beginTimer: Long){
        d(TAG, "storeUpdateTimeStart")
        mStorageManager.writeUpdateTimeStart(mRoomId, beginTimer)
    }*/

    fun storeUpdateTimeStartA(beginTimer: Long){
        d(TAG, "storeUpdateTimeStartA")
        mStorageManager.writeUpdateTimeStartA(mRoomId, beginTimer)
    }

    fun storeUpdateTimeStartB(beginTimer: Long){
        d(TAG, "storeUpdateTimeStartB")
        mStorageManager.writeUpdateTimeStartB(mRoomId, beginTimer)
    }

    /*fun storeUpdateTimeEnd(endTimer: Long){
        d(TAG, "storeUpdateTimeEnd")
        mStorageManager.writeUpdateTimeEnd(mRoomId, endTimer)
    }*/

    fun storeUpdateTimeEndA(perf: PerformanceModel){
        d(TAG, "storeUpdateTimeEndA")
        mStorageManager.writeUpdateTimeEndA(mRoomId, perf)
    }

    fun storeUpdateTimeEndB(perf: PerformanceModel){
        d(TAG, "storeUpdateTimeEndB")
        mStorageManager.writeUpdateTimeEndB(mRoomId, perf)
    }

    /*fun setOnTimeStartListener(onTimeStart: (beginTimer: Long) -> Unit) {
        d(TAG, "setOnTimeStartListener")
        this.onTimeStart = onTimeStart

        mStorageManager.readUpdateTimeStart(mRoomId) { beginTimer ->
            onTimeStart(beginTimer)
        }
    }*/

    fun setOnTimeStartListenerA(onTimeStartA: (beginTimer: Long) -> Unit) {
        d(TAG, "setOnTimeStartListenerA")
        this.onTimeStartA = onTimeStartA

        mStorageManager.readUpdateTimeStartA(mRoomId) { beginTimer ->
            onTimeStartA(beginTimer)
        }
    }

    fun setOnTimeStartListenerB(onTimeStartB: (beginTimer: Long) -> Unit) {
        d(TAG, "setOnTimeStartListenerB")
        this.onTimeStartB = onTimeStartB

        mStorageManager.readUpdateTimeStartB(mRoomId) { beginTimer ->
            onTimeStartB(beginTimer)
        }
    }

    /*fun setOnTimeEndListener(onTimeEnd: (endTimer: Long) -> Unit) {
        d(TAG, "setOnTimeEndListener")
        this.onTimeEnd = onTimeEnd

        mStorageManager.readUpdateTimeEnd(mRoomId) { endTimer ->
            onTimeEnd(endTimer)
        }
    }*/

    fun setOnTimeEndListenerA(onTimeEndA: (perf: PerformanceModel) -> Unit) {
        d(TAG, "setOnTimeEndListenerA")
        this.onTimeEndA = onTimeEndA

        mStorageManager.readUpdateTimeEndA(mRoomId) { perf ->
            onTimeEndA(perf)
        }
    }

    fun setOnTimeEndListenerB(onTimeEndB: (perf: PerformanceModel) -> Unit) {
        d(TAG, "setOnTimeEndListenerB")
        this.onTimeEndB = onTimeEndB

        mStorageManager.readUpdateTimeEndB(mRoomId) { perf ->
            onTimeEndB(perf)
        }
    }
}