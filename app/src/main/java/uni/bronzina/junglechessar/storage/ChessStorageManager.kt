package uni.bronzina.junglechessar.storage

import uni.bronzina.junglechessar.model.*
import uni.bronzina.junglechessar.util.ChessConstants
import uni.bronzina.junglechessar.util.d
import uni.bronzina.junglechessar.util.e
import com.google.firebase.database.*

/**
 * Helper class for Firebase storage of cloud anchor IDs.
 */

internal class ChessStorageManager {
    private val rootRef: DatabaseReference

    init {
        val rootDir = KEY_ROOT_DIR + ChessConstants.END_POINT.name
        rootRef = FirebaseDatabase.getInstance().reference.child(rootDir)
        DatabaseReference.goOnline()
    }

    /**
     * Gets a new short code that can be used to store the anchor ID.
     */
    fun nextRoomId(onReadroomId: (roomId: Int?) -> Unit) {
        d(TAG, "nextRoomId")
        // Run a transaction on the node containing the next short code available. This increments the
        // value in the database and retrieves it in one atomic all-or-nothing operation.
//
//        rootRef.child(KEY_NEXT_ROOM_ID).setValue(1)
//        onReadroomId(1)

        rootRef
                .child(KEY_NEXT_ROOM_ID)
                .runTransaction(
                        object : Transaction.Handler {

                            override fun doTransaction(currentData: MutableData): Transaction.Result {
                                var shortCode = currentData.getValue(Int::class.java)
                                if (shortCode == null) {
                                    shortCode = INITIAL_ROOM_ID - 1
                                }
                                currentData.value = shortCode + 1
                                return Transaction.success(currentData)
                            }

                            override fun onComplete(
                                    error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                                if (!committed) {
                                    e(TAG, "Firebase Error ${error?.toException()}")
                                    onReadroomId(null)
                                } else {
                                    if (currentData?.value == null) {
                                        onReadroomId(null)
                                    } else {
                                        val roomId = currentData.value as Long
                                        onReadroomId(roomId.toInt())
                                    }

                                }
                            }
                        })
    }

    /**
     * Stores the cloud anchor ID in the configured Firebase Database.
     */
    fun writeCloudAnchorIdUsingRoomId(shortCode: Int, cloudAnchorId: String) {
        d(TAG, "writeCloudAnchorIdUsingRoomId")
        val cloudAnchorDbModel = CloudAnchorDbModel(shortCode, cloudAnchorId, System.currentTimeMillis().toString())
        rootRef.child(shortCode.toString()).child(KEY_CONFIG).child(KEY_CLOUD_ANCHOR_CONFIG).setValue(cloudAnchorDbModel)
    }

    /**
     * Retrieves the cloud anchor ID using a short code. Returns an empty string if a cloud anchor ID
     * was not stored for this short code.
     */
    fun readCloudAnchorId(shortCode: Int, onReadCloudAnchorId: (cloudAnchorId: String?) -> Unit) {
        d(TAG, "readCloudAnchorId: $shortCode")
        rootRef
                .child(shortCode.toString())
                .child(KEY_CONFIG)
                .child(KEY_CLOUD_ANCHOR_CONFIG)
                .addListenerForSingleValueEvent(
                        object : ValueEventListener {
                            override fun onDataChange(dataSnapshot: DataSnapshot) {
                                d(TAG, "readCloudAnchorId onDataChange")
                                val cloudAnchorDbModel = dataSnapshot.getValue(CloudAnchorDbModel::class.java)
                                if (cloudAnchorDbModel != null)
                                    onReadCloudAnchorId(cloudAnchorDbModel.cloudAnchorId)
                            }

                            override fun onCancelled(error: DatabaseError) {
                                e(TAG, "The database operation for readCloudAnchorId was cancelled. ${error.toException()}")
                                onReadCloudAnchorId(null)
                            }
                        })
    }

    // this function for UserA listen UserB online event to start Game, and UserB grab UserA info to show the board

    fun readUserInfo(roomId: Int, isNeedGetUserA: Boolean, onReadUserInfo: (userInfo: ChessUserInfo) -> Unit) {
        val userRoot = if (isNeedGetUserA) KEY_USER_A else KEY_USER_B
        d(TAG, "readUserInfo: $userRoot")

        rootRef
                .child(roomId.toString())
                .child(KEY_CONFIG)
                .child(userRoot)
                .addValueEventListener(object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {
                        d(TAG, "readUserInfo onCancelled")
                    }

                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        d(TAG, "readUserInfo onDataChange")
                        val userDbModel = dataSnapshot.getValue(UserDbModel::class.java)
                        if (userDbModel != null) {
                            with(userDbModel) {
                                val userInfo = ChessUserInfo(userId, userName, userImageUrl)
                                userInfo.userType = if (userType == 0) UserType.USER_A else UserType.USER_B
                                onReadUserInfo(userInfo)
                            }

                        }
                    }

                })
    }

    fun writeUserInfo(roomId: Int, userInfo: ChessUserInfo) {
        val userRoot = if (userInfo.userType == UserType.USER_A) KEY_USER_A else KEY_USER_B
        d(TAG, "writeUserInfo, userRoot: $userRoot roomId: $roomId, userInfo: $userInfo")

        with(userInfo) {
            val userDbModel = UserDbModel(uid, userType.ordinal, photoUrl, displayName)
            rootRef.child(roomId.toString()).child(KEY_CONFIG).child(userRoot).setValue(userDbModel)
        }
    }

    fun writeGameStart(roomId: Int, isUserAStart: Boolean) {
        val gameStartRoot = if (isUserAStart) KEY_IS_USER_A_CONFIRM else KEY_IS_USER_B_CONFIRM
        d(TAG, "writeGameStart, roomId: $roomId, gameStartRoot: $gameStartRoot")
        rootRef.child(roomId.toString()).child(KEY_CONFIG).child(KEY_USER_CONFIRM_START).child(gameStartRoot).setValue(true)
    }

    fun readGameStart(roomId: Int, onReadGameStart: (isUserAReady: Boolean, isUserBReady: Boolean) -> Unit) {
        d(TAG, "readGameStart, roomId: $roomId")
        rootRef
                .child(roomId.toString())
                .child(KEY_CONFIG)
                .child(KEY_USER_CONFIRM_START)
                .addValueEventListener(object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {
                        d(TAG, "readGameStart onCancelled")
                    }

                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        d(TAG, "readGameStart onDataChange")
                        val values = dataSnapshot.value
                        if (values != null) {
                            d(TAG, "readGameStart onDataChange : $values")
                            values as HashMap<String, Boolean>
                            val isUserAConfirm = values["isUserAConfirm"] ?: false
                            val isUserBConfirm = values["isUserBConfirm"] ?: false
                            onReadGameStart(isUserAConfirm, isUserBConfirm)
                        }
                    }

                })
    }

    fun writeAnimalInfo(roomId: Int, animal1: Animal, animal2: Animal, type: AnimalDrawType){
        d(TAG, "writeAnimalInfo, roomId: $roomId, user: $type")

        if(type == AnimalDrawType.TYPE_A){
            rootRef.child(roomId.toString()).child(KEY_GAME_INFO_A).child(KEY_USER_A).child("move").setValue(animal1)
            if(animal1 != animal2){
                rootRef.child(roomId.toString()).child(KEY_GAME_INFO_A).child(KEY_USER_A).child("eat").setValue(animal2)
            }
        }

        else{
            rootRef.child(roomId.toString()).child(KEY_GAME_INFO_B).child(KEY_USER_B).child("move").setValue(animal1)
            if(animal1 != animal2){
                rootRef.child(roomId.toString()).child(KEY_GAME_INFO_B).child(KEY_USER_B).child("eat").setValue(animal2)
            }
        }
    }


    fun readAnimalInfoMove(roomId: Int, isNeedGetUserA: Boolean, onReadAnimalInfoMove: (animal: Animal) -> Unit) {
        val userRoot = if (isNeedGetUserA) KEY_USER_A else KEY_USER_B
        d(TAG, "readUserInfo: $userRoot")

        if(isNeedGetUserA){
            rootRef
                    .child(roomId.toString())
                    .child(KEY_GAME_INFO_A)
                    .child(userRoot)
                    .child("move")
                    .addValueEventListener(object : ValueEventListener {
                        override fun onCancelled(error: DatabaseError) {
                            d(TAG, "readUserInfo onCancelled")
                        }

                        override fun onDataChange(dataSnapshot: DataSnapshot) {
                            d(TAG, "readUserInfo onDataChange")
                            val animalMove = dataSnapshot.getValue(Animal::class.java)
                            if (animalMove != null) {
                                onReadAnimalInfoMove(animalMove)
                            }
                        }

                    })
        }

        else{
            rootRef
                    .child(roomId.toString())
                    .child(KEY_GAME_INFO_B)
                    .child(userRoot)
                    .child("move")
                    .addValueEventListener(object : ValueEventListener {
                        override fun onCancelled(error: DatabaseError) {
                            d(TAG, "readUserInfo onCancelled")
                        }

                        override fun onDataChange(dataSnapshot: DataSnapshot) {
                            d(TAG, "readUserInfo onDataChange")
                            val animalMove = dataSnapshot.getValue(Animal::class.java)
                            if (animalMove != null) {
                                onReadAnimalInfoMove(animalMove)
                            }
                        }

                    })
        }
    }

    fun readAnimalInfoEat(roomId: Int, isNeedGetUserA: Boolean, onReadAnimalInfoEat: (animal: Animal) -> Unit) {
        val userRoot = if (isNeedGetUserA) KEY_USER_A else KEY_USER_B
        d(TAG, "readUserInfo: $userRoot")

        if(isNeedGetUserA){
            rootRef
                    .child(roomId.toString())
                    .child(KEY_GAME_INFO_A)
                    .child(userRoot)
                    .child("eat")
                    .addValueEventListener(object : ValueEventListener {
                        override fun onCancelled(error: DatabaseError) {
                            d(TAG, "readUserInfo onCancelled")
                        }

                        override fun onDataChange(dataSnapshot: DataSnapshot) {
                            d(TAG, "readUserInfo onDataChange")
                            val animalEat = dataSnapshot.getValue(Animal::class.java)
                            if (animalEat != null) {
                                onReadAnimalInfoEat(animalEat)
                            }
                        }

                    })
        }

        else{
            rootRef
                    .child(roomId.toString())
                    .child(KEY_GAME_INFO_B)
                    .child(userRoot)
                    .child("eat")
                    .addValueEventListener(object : ValueEventListener {
                        override fun onCancelled(error: DatabaseError) {
                            d(TAG, "readUserInfo onCancelled")
                        }

                        override fun onDataChange(dataSnapshot: DataSnapshot) {
                            d(TAG, "readUserInfo onDataChange")
                            val animalEat = dataSnapshot.getValue(Animal::class.java)
                            if (animalEat != null) {
                                onReadAnimalInfoEat(animalEat)
                            }
                        }

                    })
        }
    }


    fun readTurnInfo(roomId: Int, onReadTurnInfo: (userATurn: Boolean) -> Unit){
        d(TAG, "readTurnInfo, roomId: $roomId")

        rootRef
                .child(roomId.toString())
                .child("gameInfo")
                .child(KEY_TURN)
                .addValueEventListener(object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {
                        d(TAG, "readTurnInfo onCancelled")
                    }

                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        d(TAG, "readTurnInfo onDataChange")

                        val value = dataSnapshot.getValue(Boolean::class.java)
                        if(value != null){
                            onReadTurnInfo(value)
                        }
                    }

                })
    }

    fun writeTurnInfo(roomId: Int, userATurn: Boolean){
        d(TAG, "writeTurnInfo, roomId: $roomId, userATurn: $userATurn")
        rootRef.child(roomId.toString()).child("gameInfo").child(KEY_TURN).setValue(userATurn)
    }

    fun writeGameFinish(roomId: Int, userAWin: Boolean){
        d(TAG, "writeGameFinish, roomId: $roomId, userAWin: $userAWin")
        rootRef.child(roomId.toString()).child("gameInfo").child(KEY_FINISH).setValue(userAWin)
    }

    fun readGameFinish(roomId: Int, onReadGameFinish: (userAWin: Boolean) -> Unit){
        d(TAG, "readGameFinish, roomId: $roomId")

        rootRef
                .child(roomId.toString())
                .child("gameInfo")
                .child(KEY_FINISH)
                .addValueEventListener(object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {
                        d(TAG, "readTurnInfo onCancelled")
                    }

                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        d(TAG, "readTurnInfo onDataChange")

                        val value = dataSnapshot.getValue(Boolean::class.java)
                        if(value != null){
                            onReadGameFinish(value)
                        }
                    }

                })
    }

    /*fun writeUpdateTimeStart(roomId: Int, beginTimer: Long){
        d(TAG, "writeUpdateTimeStart, roomId: $roomId, beginTimer: $beginTimer")
        rootRef.child(roomId.toString()).child(KEY_PERFORMANCE).child("start").setValue(beginTimer)
    }*/

    fun writeUpdateTimeStartA(roomId: Int, beginTimer: Long){
        d(TAG, "writeUpdateTimeStartA, roomId: $roomId, beginTimer: $beginTimer")
        rootRef.child(roomId.toString()).child(KEY_PERFORMANCE).child("A").child("start").setValue(beginTimer)
    }

    fun writeUpdateTimeStartB(roomId: Int, beginTimer: Long){
        d(TAG, "writeUpdateTimeStartB, roomId: $roomId, beginTimer: $beginTimer")
        rootRef.child(roomId.toString()).child(KEY_PERFORMANCE).child("B").child("start").setValue(beginTimer)
    }


    /*fun writeUpdateTimeEnd(roomId: Int, endTimer: Long){
        d(TAG, "writeUpdateTimeEnd, roomId: $roomId, beginTimer: $endTimer")
        rootRef.child(roomId.toString()).child(KEY_PERFORMANCE).child("end").setValue(endTimer)
    }*/

    fun writeUpdateTimeEndA(roomId: Int, perf: PerformanceModel){
        d(TAG, "writeUpdateTimeEndA, roomId: $roomId, beginTimer: ${perf.time}")
        rootRef.child(roomId.toString()).child(KEY_PERFORMANCE).child("A").child("end").setValue(perf)
    }

    fun writeUpdateTimeEndB(roomId: Int, perf: PerformanceModel){
        d(TAG, "writeUpdateTimeEndB, roomId: $roomId, beginTimer: ${perf.time}")
        rootRef.child(roomId.toString()).child(KEY_PERFORMANCE).child("B").child("end").setValue(perf)
    }

    /*fun readUpdateTimeStart(roomId: Int, onReadUpdateTimeStart: (beginTimer: Long) -> Unit){
        d(TAG, "readUpdateTimeStart, roomId: $roomId")

        rootRef
                .child(roomId.toString())
                .child(KEY_PERFORMANCE)
                .child("start")
                .addValueEventListener(object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {
                        d(TAG, "readUpdateTimeStart onCancelled")
                    }

                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        d(TAG, "readUpdateTimeStart onDataChange")

                        val value = dataSnapshot.getValue(Long::class.java)
                        if(value != null){
                            onReadUpdateTimeStart(value)
                        }
                    }

                })
    }*/

    fun readUpdateTimeStartA(roomId: Int, onReadUpdateTimeStartA: (beginTimer: Long) -> Unit){
        d(TAG, "readUpdateTimeStartA, roomId: $roomId")

        rootRef
                .child(roomId.toString())
                .child(KEY_PERFORMANCE)
                .child("A")
                .child("start")
                .addValueEventListener(object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {
                        d(TAG, "readUpdateTimeStartA onCancelled")
                    }

                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        d(TAG, "readUpdateTimeStartA onDataChange")

                        val value = dataSnapshot.getValue(Long::class.java)
                        if(value != null){
                            onReadUpdateTimeStartA(value)
                        }
                    }

                })
    }

    fun readUpdateTimeStartB(roomId: Int, onReadUpdateTimeStartB: (beginTimer: Long) -> Unit){
        d(TAG, "readUpdateTimeStartB, roomId: $roomId")

        rootRef
                .child(roomId.toString())
                .child(KEY_PERFORMANCE)
                .child("B")
                .child("start")
                .addValueEventListener(object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {
                        d(TAG, "readUpdateTimeStartB onCancelled")
                    }

                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        d(TAG, "readUpdateTimeStartB onDataChange")

                        val value = dataSnapshot.getValue(Long::class.java)
                        if(value != null){
                            onReadUpdateTimeStartB(value)
                        }
                    }

                })
    }

    /*fun readUpdateTimeEnd(roomId: Int, onReadUpdateTimeEnd: (endTimer: Long) -> Unit){
        d(TAG, "readUpdateTimeEnd, roomId: $roomId")

        rootRef
                .child(roomId.toString())
                .child(KEY_PERFORMANCE)
                .child("end")
                .addValueEventListener(object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {
                        d(TAG, "readUpdateTimeEnd onCancelled")
                    }

                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        d(TAG, "readUpdateTimeEnd onDataChange")

                        val value = dataSnapshot.getValue(Long::class.java)
                        if(value != null){
                            onReadUpdateTimeEnd(value)
                        }
                    }

                })
    }*/

    fun readUpdateTimeEndA(roomId: Int, onReadUpdateTimeEndA: (perf: PerformanceModel) -> Unit){
        d(TAG, "readUpdateTimeEndA, roomId: $roomId")

        rootRef
                .child(roomId.toString())
                .child(KEY_PERFORMANCE)
                .child("A")
                .child("end")
                .addValueEventListener(object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {
                        d(TAG, "readUpdateTimeEndA onCancelled")
                    }

                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        d(TAG, "readUpdateTimeEndA onDataChange")

                        val value = dataSnapshot.getValue(PerformanceModel::class.java)
                        if(value != null){
                            onReadUpdateTimeEndA(value)
                        }
                    }

                })
    }

    fun readUpdateTimeEndB(roomId: Int, onReadUpdateTimeEndB: (perf: PerformanceModel) -> Unit){
        d(TAG, "readUpdateTimeEndB, roomId: $roomId")

        rootRef
                .child(roomId.toString())
                .child(KEY_PERFORMANCE)
                .child("B")
                .child("end")
                .addValueEventListener(object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {
                        d(TAG, "readUpdateTimeEndB onCancelled")
                    }

                    override fun onDataChange(dataSnapshot: DataSnapshot) {
                        d(TAG, "readUpdateTimeEndB onDataChange")

                        val value = dataSnapshot.getValue(PerformanceModel::class.java)
                        if(value != null){
                            onReadUpdateTimeEndB(value)
                        }
                    }

                })
    }


    companion object {
        private val TAG = ChessStorageManager::class.java.simpleName
        const val KEY_ROOT_DIR = "animal_chess_table_"
        private const val KEY_NEXT_ROOM_ID = "next_room_id"
        private const val INITIAL_ROOM_ID = 1
        private const val KEY_CLOUD_ANCHOR_CONFIG = "cloudAnchorConfig"
        private const val KEY_USER_A = "userA"
        private const val KEY_USER_B = "userB"
        private const val KEY_USER_CONFIRM_START = "userConfirmStart"
        private const val KEY_CONFIG = "config"
        //private const val KEY_ROOM_ID = "roomId"
        private const val KEY_GAME_INFO_A = "gameInfoA"
        private const val KEY_GAME_INFO_B = "gameInfoB"
        private const val KEY_PERFORMANCE = "performance"
        private const val KEY_IS_USER_A_CONFIRM = "isUserAConfirm"
        private const val KEY_IS_USER_B_CONFIRM = "isUserBConfirm"
        private const val KEY_TURN = "userATurn"
        private const val KEY_FINISH = "winner"
    }
}
