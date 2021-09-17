package uni.bronzina.junglechessar

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.*
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import uni.bronzina.junglechessar.controller.GameController
import uni.bronzina.junglechessar.model.*
import uni.bronzina.junglechessar.storage.ChessStorageManager
import uni.bronzina.junglechessar.util.ChessConstants
import uni.bronzina.junglechessar.util.Utils
import uni.bronzina.junglechessar.util.d
import uni.bronzina.junglechessar.util.e
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.HitTestResult
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.firebase.auth.*
import com.google.firebase.database.*
import com.instacart.library.truetime.TrueTime
import java.io.*
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.absoluteValue


class MainActivity : AppCompatActivity() {

    private val RC_PERMISSIONS = 0x123
    private val RC_SIGN_IN = 234

    private var installRequested: Boolean = false

    private var gestureDetector: GestureDetector? = null
    private var loadingMessageSnackbar: Snackbar? = null
    private var arSceneView: ArSceneView? = null

    /*
    Game controller
     */
    private lateinit var gameController: GameController
    private var welcomeAnchor: Anchor? = null
    private var controllerRenderable: ViewRenderable? = null
    private var welcomeRenderable: ViewRenderable? = null
    private var controllerNode: Node = Node()
    private var welcomeNode: Node = Node()
    private val pointer = PointerDrawable()
    private var needShowWelcomePanel: Boolean = true
    private var isTracking: Boolean = false
    private var isHitting: Boolean = false

    /*
    Chess tiles
     */
    private var tilesGrassRenderable: ModelRenderable? = null
    private var tilesRiverRenderable: ModelRenderable? = null
    private var tilesATrapRenderable: ModelRenderable? = null
    private var tilesBTrapRenderable: ModelRenderable? = null
    private var tilesABasementRenderable: ModelRenderable? = null
    private var tilesBBasementRenderable: ModelRenderable? = null
    private var tilesSplierator: ViewRenderable? = null

    /*
    Chessman
     */
    private var playeAChessmen: MutableList<ChessmanNode> = ArrayList<ChessmanNode>()
    private var playeBChessmen: MutableList<ChessmanNode> = ArrayList<ChessmanNode>()
    private var playeAmouseRenderable: ModelRenderable? = null
    private var playeAcatRenderable: ModelRenderable? = null
    private var playeAdogRenderable: ModelRenderable? = null
    private var playeAwolveRenderable: ModelRenderable? = null
    private var playeAleopardRenderable: ModelRenderable? = null
    private var playeAtigerRenderable: ModelRenderable? = null
    private var playeAlionRenderable: ModelRenderable? = null
    private var playeAelephantRenderable: ModelRenderable? = null

    private var playeBmouseRenderable: ModelRenderable? = null
    private var playeBcatRenderable: ModelRenderable? = null
    private var playeBdogRenderable: ModelRenderable? = null
    private var playeBwolveRenderable: ModelRenderable? = null
    private var playeBleopardRenderable: ModelRenderable? = null
    private var playeBtigerRenderable: ModelRenderable? = null
    private var playeBlionRenderable: ModelRenderable? = null
    private var playeBelephantRenderable: ModelRenderable? = null

    // True once scene is loaded
    private var hasFinishedLoading = false


    private var hasPlacedTilesSystem = false
    private var appAnchorState = AppAnchorState.NONE
    private var cloudAnchor: Anchor? = null
    private var arSession: Session? = null
    private val TAG = MainActivity::class.java.simpleName
    private val mGameController = GameController.instance
    private val mHandler = Handler()
    private val mCheckAnchorUpdateRunnable = Runnable { checkUpdatedAnchor() }

    private var mFirebaseAuth: FirebaseAuth? = null
    private var mFirebaseUser: FirebaseUser? = null
    private var mGoogleApiClient: GoogleApiClient? = null
    private var mIsUserA: Boolean = false

    private var toolbar: Toolbar? = null
    private var tv: TextView? = null

    private var firstClick: Boolean = true
    private var col: Int = 0
    private var row: Int = 0
    private var animal: AnimalType = AnimalType.RAT
    private var centerTile: Node? = null
    private var rankA: HashMap<AnimalTypeA, Int> = HashMap()
    private var rankB: HashMap<AnimalTypeB, Int> = HashMap()
    private var occupiedTilesA: HashMap<Pair<Int, Int>, AnimalTypeA> = HashMap()
    private var occupiedTilesB: HashMap<Pair<Int, Int>, AnimalTypeB> = HashMap()

    private var mCurrentGameState = GameState.USER_A_TURN
    private lateinit var rootRef: DatabaseReference
    private var isGameStarted: Boolean = false
    private var countDownTimer: CountDownTimer? = null

    private var beginTimer: Long = 0
    private var endTimer: Long = 0

    //Line chart
    private var startTimeA: Long = 0
    private var startTimeB: Long = 0
    private var i: Int = 0
    private var indexStart: Int = 0
    private var indexEnd: Int = 0
    private var moveStartA: HashMap<Int, Long> = HashMap()
    private var moveStartB: HashMap<Int, Long> = HashMap()
    private var lagA: HashMap<Int, Long> = HashMap()
    private var lagB: HashMap<Int, Long> = HashMap()
    public var scoreList: ArrayList<Score> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        mFirebaseAuth = FirebaseAuth.getInstance()
        mFirebaseUser = mFirebaseAuth!!.currentUser

        val rootDir = ChessStorageManager.KEY_ROOT_DIR + ChessConstants.END_POINT.name
        rootRef = FirebaseDatabase.getInstance().reference.child(rootDir)

        gameController = GameController.instance
        arSceneView = findViewById(R.id.ar_scene_view)

        initTrueTime(this)

        val panel_welcome = ViewRenderable.builder().setView(this, R.layout.panel_welcome).build();
        val panel_controller = ViewRenderable.builder().setView(this, R.layout.panel_controller).build();
        val tile_split = ViewRenderable.builder().setView(this, R.layout.spliter_tiles).build();

        val tiles_grass = ModelRenderable.builder().setSource(this, Uri.parse("trees1.sfb")).build()
        val tiles_river = ModelRenderable.builder().setSource(this, Uri.parse("Wave.sfb")).build()
        val tilesA_trap = ModelRenderable.builder().setSource(this, Uri.parse("SM_Castle.sfb")).build()
        val tilesB_trap = ModelRenderable.builder().setSource(this, Uri.parse("SM_Castle.sfb")).build()
        val tilesA_basement = ModelRenderable.builder().setSource(this, Uri.parse("model.sfb")).build()
        val tilesB_basement = ModelRenderable.builder().setSource(this, Uri.parse("model.sfb")).build()

        val playA_chessman_mouse = ModelRenderable.builder().setSource(this, Uri.parse("Mesh_Hamster.sfb")).build()
        val playA_chessman_cat = ModelRenderable.builder().setSource(this, Uri.parse("Mesh_Cat.sfb")).build()
        val playA_chessman_dog = ModelRenderable.builder().setSource(this, Uri.parse("Mesh_Beagle.sfb")).build()
        val playA_chessman_wolf = ModelRenderable.builder().setSource(this, Uri.parse("Mesh_Wolf.sfb")).build()
        val playA_chessman_leopard = ModelRenderable.builder().setSource(this, Uri.parse("Mesh_Leopard.sfb")).build()
        val playA_chessman_tiger = ModelRenderable.builder().setSource(this, Uri.parse("Mesh_BengalTiger.sfb")).build()
        val playA_chessman_lion = ModelRenderable.builder().setSource(this, Uri.parse("Mesh_Lion.sfb")).build()
        val playA_chessman_elephant = ModelRenderable.builder().setSource(this, Uri.parse("Elephant.sfb")).build()

        val playB_chessman_mouse = ModelRenderable.builder().setSource(this, Uri.parse("Mesh_Hamster.sfb")).build()
        val playB_chessman_cat = ModelRenderable.builder().setSource(this, Uri.parse("Mesh_Cat.sfb")).build()
        val playB_chessman_dog = ModelRenderable.builder().setSource(this, Uri.parse("Mesh_Beagle.sfb")).build()
        val playB_chessman_wolf = ModelRenderable.builder().setSource(this, Uri.parse("Mesh_Wolf.sfb")).build()
        val playB_chessman_leopard = ModelRenderable.builder().setSource(this, Uri.parse("Mesh_Leopard.sfb")).build()
        val playB_chessman_tiger = ModelRenderable.builder().setSource(this, Uri.parse("Mesh_BengalTiger.sfb")).build()
        val playB_chessman_lion = ModelRenderable.builder().setSource(this, Uri.parse("Mesh_Lion.sfb")).build()
        val playB_chessman_elephant = ModelRenderable.builder().setSource(this, Uri.parse("Elephant.sfb")).build()

        rankA[AnimalTypeA.RAT] = 1
        rankA[AnimalTypeA.CAT] = 2
        rankA[AnimalTypeA.DOG] = 3
        rankA[AnimalTypeA.WOLF] = 4
        rankA[AnimalTypeA.LEOPARD] = 5
        rankA[AnimalTypeA.TIGER] = 6
        rankA[AnimalTypeA.LION] = 7
        rankA[AnimalTypeA.ELEPHANT] = 8

        rankB[AnimalTypeB.RAT] = 1
        rankB[AnimalTypeB.CAT] = 2
        rankB[AnimalTypeB.DOG] = 3
        rankB[AnimalTypeB.WOLF] = 4
        rankB[AnimalTypeB.LEOPARD] = 5
        rankB[AnimalTypeB.TIGER] = 6
        rankB[AnimalTypeB.LION] = 7
        rankB[AnimalTypeB.ELEPHANT] = 8

        occupiedTilesB[Pair(0, 0)] = AnimalTypeB.LION
        occupiedTilesB[Pair(6, 0)] = AnimalTypeB.TIGER
        occupiedTilesB[Pair(1, 1)] = AnimalTypeB.DOG
        occupiedTilesB[Pair(5, 1)] = AnimalTypeB.CAT
        occupiedTilesB[Pair(0, 2)] = AnimalTypeB.RAT
        occupiedTilesB[Pair(2, 2)] = AnimalTypeB.LEOPARD
        occupiedTilesB[Pair(4, 2)] = AnimalTypeB.WOLF
        occupiedTilesB[Pair(6, 2)] = AnimalTypeB.ELEPHANT

        occupiedTilesA[Pair(0, 6)] = AnimalTypeA.ELEPHANT
        occupiedTilesA[Pair(2, 6)] = AnimalTypeA.WOLF
        occupiedTilesA[Pair(4, 6)] = AnimalTypeA.LEOPARD
        occupiedTilesA[Pair(6, 6)] = AnimalTypeA.RAT
        occupiedTilesA[Pair(1, 5)] = AnimalTypeA.CAT
        occupiedTilesA[Pair(5, 5)] = AnimalTypeA.DOG
        occupiedTilesA[Pair(0, 8)] = AnimalTypeA.TIGER
        occupiedTilesA[Pair(6, 8)] = AnimalTypeA.LION


        val fab = findViewById<FloatingActionButton>(R.id.fab)
        fab.setOnClickListener {
            mGameController.test()
        }

        CompletableFuture.allOf(
            tiles_grass,
            tiles_river,
            tilesA_trap,
            tilesB_trap,
            tilesA_basement,
            tilesB_basement,
            playA_chessman_mouse,
            playA_chessman_cat,
            playA_chessman_dog,
            playA_chessman_wolf,
            playA_chessman_leopard,
            playA_chessman_tiger,
            playA_chessman_lion,
            playA_chessman_elephant,
            playB_chessman_mouse,
            playB_chessman_cat,
            playB_chessman_dog,
            playB_chessman_wolf,
            playB_chessman_leopard,
            playB_chessman_tiger,
            playB_chessman_lion,
            playB_chessman_elephant).handle<Any> { notUsed, throwable ->
            if (throwable != null) {
                Utils.displayError(this, "Unable to load renderable", throwable)
                return@handle null
            }

            try {
                welcomeRenderable = panel_welcome.get()
                controllerRenderable = panel_controller.get()
                tilesSplierator = tile_split.get()
                tilesGrassRenderable = tiles_grass.get()
                tilesRiverRenderable = tiles_river.get()
                tilesATrapRenderable = tilesA_trap.get()
                tilesBTrapRenderable = tilesB_trap.get()
                tilesABasementRenderable = tilesA_basement.get()
                tilesBBasementRenderable = tilesB_basement.get()

                playeAmouseRenderable = playA_chessman_mouse.get()
                playeAcatRenderable = playA_chessman_cat.get()
                playeAdogRenderable = playA_chessman_dog.get()
                playeAwolveRenderable = playA_chessman_wolf.get()
                playeAleopardRenderable = playA_chessman_leopard.get()
                playeAtigerRenderable = playA_chessman_tiger.get()
                playeAlionRenderable = playA_chessman_lion.get()
                playeAelephantRenderable = playA_chessman_elephant.get()

                playeBmouseRenderable = playB_chessman_mouse.get()
                playeBcatRenderable = playB_chessman_cat.get()
                playeBdogRenderable = playB_chessman_dog.get()
                playeBwolveRenderable = playB_chessman_wolf.get()
                playeBleopardRenderable = playB_chessman_leopard.get()
                playeBtigerRenderable = playB_chessman_tiger.get()
                playeBlionRenderable = playB_chessman_lion.get()
                playeBelephantRenderable = playB_chessman_elephant.get()
                // Everything finished loading successfully.
                hasFinishedLoading = true

            } catch (ex: InterruptedException) {
                Utils.displayError(this, "Unable to load renderable", ex)
            } catch (ex: ExecutionException) {
                Utils.displayError(this, "Unable to load renderable", ex)
            }

            null
        }

        mGameController.getAnimalInfoMove(false, this::onAnimalMoveUpdate)
        mGameController.getAnimalInfoEat(false, this::onAnimalEatUpdate)

        gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    onSingleTap(e)
                    return true
                }

                override fun onDown(e: MotionEvent): Boolean {
                    return true
                }
            })

        arSceneView!!
            .scene
            .setOnTouchListener { hitTestResult: HitTestResult, event: MotionEvent ->
                // If the chessboard hasn't been placed yet, detect a tap and then check to see if
                // the tap occurred on an ARCore plane to place the chessboard.


                if (!hasPlacedTilesSystem) {
                    return@setOnTouchListener gestureDetector!!.onTouchEvent(event)
                }

                if (hitTestResult.getNode() != null) {
                    //We have hit an AR node
                    Log.d(TAG, "hitTestResult.getNode() != null: " + hitTestResult.getNode());
                    val hitNode: Node? = hitTestResult.node
                    val rend: Renderable? = hitNode?.renderable

                    if(mCurrentGameState == GameState.USER_A_TURN && isGameStarted){

                        if(firstClick){
                            if(rend == tilesRiverRenderable || rend == tilesGrassRenderable
                                || rend == tilesABasementRenderable || rend == tilesBBasementRenderable
                                || rend == tilesATrapRenderable || rend == tilesBTrapRenderable) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Touched tile, click animal first!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                firstClick = true
                            }

                            else{
                                firstClick = false
                                for(chessman in playeAChessmen) {
                                    if (chessman.chessRenderable == rend) {
                                        animal = chessman.animal.animalType
                                        col = chessman.animal.posCol
                                        row = chessman.animal.posRow
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Touched " + animal.toString() + ", col: " + col.toString() + ", row: " + row.toString(),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        }

                        else{
                            if(rend == tilesGrassRenderable || rend == tilesATrapRenderable){
                                val pos: String? = hitNode?.name
                                val list: List<String>? = pos?.split("")
                                if(list != null){
                                    val col2 = list[1].toInt()
                                    val row2 = list[2].toInt()

                                    if(((col2-col).absoluteValue == 1 && (row2-row).absoluteValue == 0) ||
                                        ((col2-col).absoluteValue == 0 && (row2-row).absoluteValue == 1)){

                                        if(animal == AnimalType.DOG){
                                            eatOrMove(col2, row2, playeAdogRenderable, AnimalType.DOG, AnimalTypeA.DOG)
                                        }
                                        else if(animal == AnimalType.RAT){
                                            eatOrMove(col2, row2, playeAmouseRenderable, AnimalType.RAT, AnimalTypeA.RAT)
                                        }
                                        else if(animal == AnimalType.CAT){
                                            eatOrMove(col2, row2, playeAcatRenderable, AnimalType.CAT, AnimalTypeA.CAT)
                                        }
                                        else if(animal == AnimalType.WOLF){
                                            eatOrMove(col2, row2, playeAwolveRenderable, AnimalType.WOLF, AnimalTypeA.WOLF)
                                        }
                                        else if(animal == AnimalType.LEOPARD){
                                            eatOrMove(col2, row2, playeAleopardRenderable, AnimalType.LEOPARD, AnimalTypeA.LEOPARD)
                                        }
                                        else if(animal == AnimalType.TIGER){
                                            eatOrMove(col2, row2, playeAtigerRenderable, AnimalType.TIGER, AnimalTypeA.TIGER)
                                        }
                                        else if(animal == AnimalType.LION){
                                            eatOrMove(col2, row2, playeAlionRenderable, AnimalType.LION, AnimalTypeA.LION)
                                        }
                                        else if(animal == AnimalType.ELEPHANT){
                                            eatOrMove(col2, row2, playeAelephantRenderable, AnimalType.ELEPHANT, AnimalTypeA.ELEPHANT)
                                        }

                                        mGameController.updateTurnInfo(false)
                                        startTimer(true)
                                    }
                                    else{
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Move is allowed in an horizontal or vertical square",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                            else if(rend == tilesRiverRenderable){
                                val pos: String? = hitNode?.name
                                val list: List<String>? = pos?.split("")
                                if(list != null) {
                                    var col2 = list[1].toInt()
                                    var row2 = list[2].toInt()

                                    if(((col2-col).absoluteValue == 1 && (row2-row).absoluteValue == 0) ||
                                        ((col2-col).absoluteValue == 0 && (row2-row).absoluteValue == 1)){

                                        if(animal == AnimalType.RAT){
                                            eatOrMove(col2, row2, playeAmouseRenderable, AnimalType.RAT, AnimalTypeA.RAT)

                                            mGameController.updateTurnInfo(false)
                                            startTimer(true)
                                        }

                                        else if(animal == AnimalType.LION){
                                            if((col2-col).absoluteValue == 1 && (row2-row).absoluteValue == 0){
                                                if(col2 < col){
                                                    col2 -= 2
                                                }
                                                else{
                                                    col2 += 2
                                                }
                                            }
                                            else{
                                                if(row2 < row){
                                                    row2 -= 3
                                                }
                                                else{
                                                    row2 += 3
                                                }
                                            }
                                            eatOrMove(col2, row2, playeAlionRenderable, AnimalType.LION, AnimalTypeA.LION)

                                            mGameController.updateTurnInfo(false)
                                            startTimer(true)
                                        }
                                        else if(animal == AnimalType.TIGER){
                                            if((col2-col).absoluteValue == 1 && (row2-row).absoluteValue == 0){
                                                if(col2 < col){
                                                    col2 -= 2
                                                }
                                                else{
                                                    col2 += 2
                                                }
                                            }
                                            else{
                                                if(row2 < row){
                                                    row2 -= 3
                                                }
                                                else{
                                                    row2 += 3
                                                }
                                            }
                                            eatOrMove(col2, row2, playeAtigerRenderable, AnimalType.TIGER, AnimalTypeA.TIGER)

                                            mGameController.updateTurnInfo(false)
                                            startTimer(true)
                                        }
                                        else{
                                            Toast.makeText(
                                                this@MainActivity,
                                                "This animal cannot go on the river",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }

                                    }
                                }
                            }
                            else if(rend == playeAdogRenderable || rend == playeAmouseRenderable || rend == playeAcatRenderable ||
                                rend == playeAelephantRenderable || rend == playeAleopardRenderable || rend == playeAlionRenderable ||
                                rend == playeAwolveRenderable || rend == playeAtigerRenderable){

                                Toast.makeText(
                                    this@MainActivity,
                                    "Square already occupied by an animal of yours",
                                    Toast.LENGTH_SHORT
                                ).show()

                            }
                            else if(rend == tilesABasementRenderable){
                                Toast.makeText(
                                    this@MainActivity,
                                    "Cannot occupy your castle",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            else if(rend == tilesBTrapRenderable){
                                val pos: String? = hitNode?.name
                                val list: List<String>? = pos?.split("")
                                if(list != null) {
                                    val col2 = list[1].toInt()
                                    val row2 = list[2].toInt()

                                    if (((col2 - col).absoluteValue == 1 && (row2 - row).absoluteValue == 0) ||
                                        ((col2 - col).absoluteValue == 0 && (row2 - row).absoluteValue == 1)
                                    ) {

                                        if (animal == AnimalType.DOG) {
                                            eatOrMoveTrap(col2, row2, playeAdogRenderable, AnimalType.DOG, AnimalTypeA.DOG)
                                        }
                                        else if (animal == AnimalType.RAT) {
                                            eatOrMoveTrap(col2, row2, playeAmouseRenderable, AnimalType.RAT, AnimalTypeA.RAT)
                                        }
                                        else if (animal == AnimalType.CAT) {
                                            eatOrMoveTrap(col2, row2, playeAcatRenderable, AnimalType.CAT, AnimalTypeA.CAT)
                                        }
                                        else if (animal == AnimalType.WOLF) {
                                            eatOrMoveTrap(col2, row2, playeAwolveRenderable, AnimalType.WOLF, AnimalTypeA.WOLF)
                                        }
                                        else if (animal == AnimalType.LEOPARD) {
                                            eatOrMoveTrap(col2, row2, playeAleopardRenderable, AnimalType.LEOPARD, AnimalTypeA.LEOPARD)
                                        } else if (animal == AnimalType.TIGER) {
                                            eatOrMoveTrap(col2, row2, playeAtigerRenderable, AnimalType.TIGER, AnimalTypeA.TIGER)
                                        } else if (animal == AnimalType.LION) {
                                            eatOrMoveTrap(col2, row2, playeAlionRenderable, AnimalType.LION, AnimalTypeA.LION)
                                        } else if (animal == AnimalType.ELEPHANT) {
                                            eatOrMoveTrap(col2, row2, playeAelephantRenderable, AnimalType.ELEPHANT, AnimalTypeA.ELEPHANT)
                                        }

                                        mGameController.updateTurnInfo(false)
                                        startTimer(true)
                                    }
                                }
                            }
                            else if(rend == playeBdogRenderable || rend == playeBmouseRenderable || rend == playeBcatRenderable ||
                                rend == playeBelephantRenderable || rend == playeBleopardRenderable || rend == playeBlionRenderable ||
                                rend == playeBwolveRenderable || rend == playeBtigerRenderable){

                                var col2 = 0
                                var row2 = 0
                                for(chessman in playeBChessmen){
                                    if(chessman.chessRenderable == rend){
                                        col2 = chessman.animal.posCol
                                        row2 = chessman.animal.posRow
                                    }
                                }

                                if(((col2-col).absoluteValue == 1 && (row2-row).absoluteValue == 0) ||
                                    ((col2-col).absoluteValue == 0 && (row2-row).absoluteValue == 1)){

                                    if(animal == AnimalType.DOG){
                                        eatOrMove(col2, row2, playeAdogRenderable, AnimalType.DOG, AnimalTypeA.DOG)
                                    }
                                    else if(animal == AnimalType.RAT){
                                        eatOrMove(col2, row2, playeAmouseRenderable, AnimalType.RAT, AnimalTypeA.RAT)
                                    }
                                    else if(animal == AnimalType.CAT){
                                        eatOrMove(col2, row2, playeAcatRenderable, AnimalType.CAT, AnimalTypeA.CAT)
                                    }
                                    else if(animal == AnimalType.WOLF){
                                        eatOrMove(col2, row2, playeAwolveRenderable, AnimalType.WOLF, AnimalTypeA.WOLF)
                                    }
                                    else if(animal == AnimalType.LEOPARD){
                                        eatOrMove(col2, row2, playeAleopardRenderable, AnimalType.LEOPARD, AnimalTypeA.LEOPARD)
                                    }
                                    else if(animal == AnimalType.TIGER){
                                        eatOrMove(col2, row2, playeAtigerRenderable, AnimalType.TIGER, AnimalTypeA.TIGER)
                                    }
                                    else if(animal == AnimalType.LION){
                                        eatOrMove(col2, row2, playeAlionRenderable, AnimalType.LION, AnimalTypeA.LION)
                                    }
                                    else if(animal == AnimalType.ELEPHANT){
                                        eatOrMove(col2, row2, playeAelephantRenderable, AnimalType.ELEPHANT, AnimalTypeA.ELEPHANT)
                                    }

                                    mGameController.updateTurnInfo(false)
                                    startTimer(true)
                                }
                            }
                            else if(rend == tilesBBasementRenderable){
                                val pos: String? = hitNode?.name
                                val list: List<String>? = pos?.split("")
                                if(list != null) {
                                    val col2 = list[1].toInt()
                                    val row2 = list[2].toInt()

                                    if (((col2 - col).absoluteValue == 1 && (row2 - row).absoluteValue == 0) ||
                                        ((col2 - col).absoluteValue == 0 && (row2 - row).absoluteValue == 1)
                                    ) {
                                        mGameController.updateGameFinish(true)
                                    }
                                }

                            }
                            firstClick = true
                        }

                    }

                }

                // Otherwise return false so that the touch event can propagate to the scene.
                false

            }

        arSceneView!!
            .scene
            .setOnUpdateListener { frameTime ->

                if (needShowWelcomePanel) {
                    onUpdate()
                }

                if (loadingMessageSnackbar == null) {
                    return@setOnUpdateListener
                }

                val frame = arSceneView!!.arFrame ?: return@setOnUpdateListener

                if (frame.camera.trackingState != TrackingState.TRACKING) {
                    return@setOnUpdateListener
                }

                for (plane in frame.getUpdatedTrackables(Plane::class.java)) {
                    if (plane.trackingState == TrackingState.TRACKING) {
                        //hideLoadingMessage()
                        showWelcomeMessage()
                    }
                }


            }

        Utils.requestCameraPermission(this, RC_PERMISSIONS)

    }

    private fun getTrueTime(): Long {
        if (TrueTime.isInitialized()){
            val localDateTime = TrueTime.now()
            return localDateTime.time
        }
        else{
            return 0
        }
    }

    private fun initTrueTime(ctx: Context) {
        if (isNetworkConnected(ctx)) {
            if (!TrueTime.isInitialized()) {
                val trueTime = InitTrueTimeAsyncTask(ctx)
                trueTime.execute()
            }
        }
    }

    private fun isNetworkConnected(ctx: Context): Boolean {
        val cm = ctx
            .getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val ni = cm.activeNetworkInfo
        return ni != null && ni.isConnectedOrConnecting
    }

    private fun startTimer(userA: Boolean){
        //beginTimer = System.nanoTime()
        beginTimer = getTrueTime()
        if(userA){
            //mGameController.storeUpdateTimeStartA(beginTimer)
            moveStartA[indexStart] = beginTimer
            indexStart++
        }
        else{
            //mGameController.storeUpdateTimeStartB(beginTimer)
            moveStartB[indexStart] = beginTimer
            indexStart++
        }
    }

    private fun endTimer(userA: Boolean){
        //endTimer = System.nanoTime()
        endTimer = getTrueTime()
        var perf = PerformanceModel(indexEnd, endTimer)

        if(userA){
            mGameController.storeUpdateTimeEndA(perf)
        }
        else{
            mGameController.storeUpdateTimeEndB(perf)
        }

        indexEnd++

    }


    private fun eatOrMoveTrapB(newCol: Int, newRow: Int, renderable: ModelRenderable?, type: AnimalType, typeB: AnimalTypeB){
        if(occupiedTilesA.containsKey(Pair(newCol, newRow))){
            val victim = occupiedTilesA[Pair(newCol, newRow)]
            if(rankA[victim]!! <= rankB[typeB]!!){
                eatChessmanA(newCol, newRow, renderable, typeB.index - 1, victim, victim!!.index - 1)
                occupiedTilesA.remove(Pair(newCol, newRow))
            }
            else{
                Toast.makeText(
                    this@MainActivity,
                    "Cannot eat an animal with greater rank!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        else{
            moveChessmanB(newCol, newRow, renderable, type, typeB.index - 1)
        }
        val iterator = occupiedTilesB.iterator()
        while(iterator.hasNext()){
            val fieldValue = iterator.next()
            if(fieldValue.value == typeB){
                iterator.remove()
            }
        }
        occupiedTilesB[Pair(newCol, newRow)] = typeB
        for(chessman in playeBChessmen){
            if(chessman.chessRenderable == renderable){
                chessman.animal.inTrap = true
                rankB[typeB] = 0
            }
        }
    }

    private fun eatOrMoveB(newCol: Int, newRow: Int, renderable: ModelRenderable?, type: AnimalType, typeB: AnimalTypeB){
        if(occupiedTilesA.containsKey(Pair(newCol, newRow))){
            val victim = occupiedTilesA[Pair(newCol, newRow)]
            if(rankA[victim]!! <= rankB[typeB]!!){
                eatChessmanA(newCol, newRow, renderable, typeB.index - 1, victim, victim!!.index - 1)
                occupiedTilesA.remove(Pair(newCol, newRow))
            }
            else{
                Toast.makeText(
                    this@MainActivity,
                    "Cannot eat an animal with greater rank!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        else{
            moveChessmanB(newCol, newRow, renderable, type, typeB.index - 1)
        }
        val iterator = occupiedTilesB.iterator()
        while(iterator.hasNext()){
            val fieldValue = iterator.next()
            if(fieldValue.value == typeB){
                iterator.remove()
            }
        }
        occupiedTilesB[Pair(newCol, newRow)] = typeB
        for(chessman in playeBChessmen){
            if(chessman.chessRenderable == renderable){
                chessman.animal.inTrap = false
                rankB[typeB] = typeB.index
            }
        }
    }

    private fun eatOrMoveTrap(newCol: Int, newRow: Int, renderable: ModelRenderable?, type: AnimalType, typeA: AnimalTypeA){
        if(occupiedTilesB.containsKey(Pair(newCol, newRow))){
            val victim = occupiedTilesB[Pair(newCol, newRow)]
            if(rankB[victim]!! <= rankA[typeA]!!){
                eatChessmanB(newCol, newRow, renderable, typeA.index - 1, victim, victim!!.index - 1)
                occupiedTilesB.remove(Pair(newCol, newRow))
            }
            else{
                Toast.makeText(
                    this@MainActivity,
                    "Cannot eat an animal with greater rank!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        else{
            moveChessman(newCol, newRow, renderable, type, typeA.index - 1)
        }
        val iterator = occupiedTilesA.iterator()
        while(iterator.hasNext()){
            val fieldValue = iterator.next()
            if(fieldValue.value == typeA){
                iterator.remove()
            }
        }
        occupiedTilesA[Pair(newCol, newRow)] = typeA
        for(chessman in playeAChessmen){
            if(chessman.chessRenderable == renderable){
                chessman.animal.inTrap = true
                rankA[typeA] = 0
            }
        }
    }

    private fun eatOrMove(newCol: Int, newRow: Int, renderable: ModelRenderable?, type: AnimalType, typeA: AnimalTypeA){
        if(occupiedTilesB.containsKey(Pair(newCol, newRow))){
            val victim = occupiedTilesB[Pair(newCol, newRow)]
            if(rankB[victim]!! <= rankA[typeA]!!){
                eatChessmanB(newCol, newRow, renderable, typeA.index - 1, victim, victim!!.index - 1)
                occupiedTilesB.remove(Pair(newCol, newRow))
            }
            else{
                Toast.makeText(
                    this@MainActivity,
                    "Cannot eat an animal with greater rank!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        else{
            moveChessman(newCol, newRow, renderable, type, typeA.index - 1)
        }
        val iterator = occupiedTilesA.iterator()
        while(iterator.hasNext()){
            val fieldValue = iterator.next()
            if(fieldValue.value == typeA){
                iterator.remove()
            }
        }
        occupiedTilesA[Pair(newCol, newRow)] = typeA
        for(chessman in playeAChessmen){
            if(chessman.chessRenderable == renderable){
                chessman.animal.inTrap = false
                rankA[typeA] = typeA.index
            }
        }
    }

    private fun eatChessmanB(newCol: Int, newRow: Int, renderable: ModelRenderable?, index: Int, victim: AnimalTypeB?, victimIndex: Int){
        for(node in centerTile!!.children){
            if(node.renderable == renderable){
                node.renderable = null
            }
        }

        var animalType: AnimalType = AnimalType.RAT
        if(renderable == playeAmouseRenderable){
            animalType = AnimalType.RAT
        }
        else if(renderable == playeAcatRenderable){
            animalType = AnimalType.CAT
        }
        else if(renderable == playeAdogRenderable){
            animalType = AnimalType.DOG
        }
        else if(renderable == playeAwolveRenderable){
            animalType = AnimalType.WOLF
        }
        else if(renderable == playeAleopardRenderable){
            animalType = AnimalType.LEOPARD
        }
        else if(renderable == playeAtigerRenderable){
            animalType = AnimalType.TIGER
        }
        else if(renderable == playeAlionRenderable){
            animalType = AnimalType.LION
        }
        else if(renderable == playeAelephantRenderable){
            animalType = AnimalType.ELEPHANT
        }

        var victimRenderable: ModelRenderable? = null
        var victimType: AnimalType = AnimalType.RAT
        if(victim == AnimalTypeB.RAT){
            victimRenderable = playeBmouseRenderable
            victimType = AnimalType.RAT
        }
        else if(victim == AnimalTypeB.CAT){
            victimRenderable = playeBcatRenderable
            victimType = AnimalType.CAT
        }
        else if(victim == AnimalTypeB.DOG){
            victimRenderable = playeBdogRenderable
            victimType = AnimalType.DOG
        }
        else if(victim == AnimalTypeB.WOLF){
            victimRenderable = playeBwolveRenderable
            victimType = AnimalType.WOLF
        }
        else if(victim == AnimalTypeB.LEOPARD){
            victimRenderable = playeBleopardRenderable
            victimType = AnimalType.LEOPARD
        }
        else if(victim == AnimalTypeB.TIGER){
            victimRenderable = playeBtigerRenderable
            victimType = AnimalType.TIGER
        }
        else if(victim == AnimalTypeB.LION){
            victimRenderable = playeBlionRenderable
            victimType = AnimalType.LION
        }
        else if(victim == AnimalTypeB.ELEPHANT){
            victimRenderable = playeBelephantRenderable
            victimType = AnimalType.ELEPHANT
        }

        removeChessmanB(victimRenderable, victimIndex)

        val new = ChessmanNode(this,
            Animal(newCol, newRow, AnimalState.ALIVE, animalType),
            renderable!!)

        playeAChessmen[index] = new
        placeChessmen(centerTile!!)

        mGameController.updateGameInfo(Animal(newCol, newRow, AnimalState.ALIVE, animalType, AnimalDrawType.TYPE_A),
            Animal(newCol, newRow, AnimalState.DEAD, victimType, AnimalDrawType.TYPE_B))

    }

    private fun removeChessmanB(victimRenderable: ModelRenderable?, victimIndex: Int){

        for(node in centerTile!!.children){
            if(node.renderable == victimRenderable){
                node.renderable = null
            }
        }

        playeBChessmen.removeAt(victimIndex)

        if(victimRenderable == playeBmouseRenderable){
            AnimalTypeB.CAT.index -= 1
            AnimalTypeB.DOG.index -= 1
            AnimalTypeB.WOLF.index -= 1
            AnimalTypeB.LEOPARD.index -= 1
            AnimalTypeB.TIGER.index -= 1
            AnimalTypeB.LION.index -= 1
            AnimalTypeB.ELEPHANT.index -= 1
        }
        else if(victimRenderable == playeBcatRenderable){
            AnimalTypeB.DOG.index -= 1
            AnimalTypeB.WOLF.index -= 1
            AnimalTypeB.LEOPARD.index -= 1
            AnimalTypeB.TIGER.index -= 1
            AnimalTypeB.LION.index -= 1
            AnimalTypeB.ELEPHANT.index -= 1
        }
        else if(victimRenderable == playeBdogRenderable){
            AnimalTypeB.WOLF.index -= 1
            AnimalTypeB.LEOPARD.index -= 1
            AnimalTypeB.TIGER.index -= 1
            AnimalTypeB.LION.index -= 1
            AnimalTypeB.ELEPHANT.index -= 1
        }
        else if(victimRenderable == playeBwolveRenderable){
            AnimalTypeB.LEOPARD.index -= 1
            AnimalTypeB.TIGER.index -= 1
            AnimalTypeB.LION.index -= 1
            AnimalTypeB.ELEPHANT.index -= 1
        }
        else if(victimRenderable == playeBleopardRenderable){
            AnimalTypeB.TIGER.index -= 1
            AnimalTypeB.LION.index -= 1
            AnimalTypeB.ELEPHANT.index -= 1
        }
        else if(victimRenderable == playeBtigerRenderable){
            AnimalTypeB.LION.index -= 1
            AnimalTypeB.ELEPHANT.index -= 1
        }
        else if(victimRenderable == playeBlionRenderable){
            AnimalTypeB.ELEPHANT.index -= 1
        }

        placeChessmen(centerTile!!)
    }

    private fun eatChessmanA(newCol: Int, newRow: Int, renderable: ModelRenderable?, index: Int, victim: AnimalTypeA?, victimIndex: Int){
        for(node in centerTile!!.children){
            if(node.renderable == renderable){
                node.renderable = null
            }
        }

        var animalType: AnimalType = AnimalType.RAT
        if(renderable == playeBmouseRenderable){
            animalType = AnimalType.RAT
        }
        else if(renderable == playeBcatRenderable){
            animalType = AnimalType.CAT
        }
        else if(renderable == playeBdogRenderable){
            animalType = AnimalType.DOG
        }
        else if(renderable == playeBwolveRenderable){
            animalType = AnimalType.WOLF
        }
        else if(renderable == playeBleopardRenderable){
            animalType = AnimalType.LEOPARD
        }
        else if(renderable == playeBtigerRenderable){
            animalType = AnimalType.TIGER
        }
        else if(renderable == playeBlionRenderable){
            animalType = AnimalType.LION
        }
        else if(renderable == playeBelephantRenderable){
            animalType = AnimalType.ELEPHANT
        }

        var victimRenderable: ModelRenderable? = null
        var victimType: AnimalType = AnimalType.RAT
        if(victim == AnimalTypeA.RAT){
            victimRenderable = playeAmouseRenderable
            victimType = AnimalType.RAT
        }
        else if(victim == AnimalTypeA.CAT){
            victimRenderable = playeAcatRenderable
            victimType = AnimalType.CAT
        }
        else if(victim == AnimalTypeA.DOG){
            victimRenderable = playeAdogRenderable
            victimType = AnimalType.DOG
        }
        else if(victim == AnimalTypeA.WOLF){
            victimRenderable = playeAwolveRenderable
            victimType = AnimalType.WOLF
        }
        else if(victim == AnimalTypeA.LEOPARD){
            victimRenderable = playeAleopardRenderable
            victimType = AnimalType.LEOPARD
        }
        else if(victim == AnimalTypeA.TIGER){
            victimRenderable = playeAtigerRenderable
            victimType = AnimalType.TIGER
        }
        else if(victim == AnimalTypeA.LION){
            victimRenderable = playeAlionRenderable
            victimType = AnimalType.LION
        }
        else if(victim == AnimalTypeA.ELEPHANT){
            victimRenderable = playeAelephantRenderable
            victimType = AnimalType.ELEPHANT
        }

        removeChessmanA(victimRenderable, victimIndex)


        val new = ChessmanNode(this,
            Animal(newCol, newRow, AnimalState.ALIVE, animalType),
            renderable!!)

        playeBChessmen[index] = new
        placeChessmen(centerTile!!)

        mGameController.updateGameInfo(Animal(newCol, newRow, AnimalState.ALIVE, animalType, AnimalDrawType.TYPE_B),
            Animal(newCol, newRow, AnimalState.DEAD, victimType, AnimalDrawType.TYPE_A))

    }

    private fun removeChessmanA(victimRenderable: ModelRenderable?, victimIndex: Int){
        for(node in centerTile!!.children) {
            if (node.renderable == victimRenderable) {
                node.renderable = null
            }
        }

        playeAChessmen.removeAt(victimIndex)

        if(victimRenderable == playeBmouseRenderable){
            AnimalTypeA.CAT.index -= 1
            AnimalTypeA.DOG.index -= 1
            AnimalTypeA.WOLF.index -= 1
            AnimalTypeA.LEOPARD.index -= 1
            AnimalTypeA.TIGER.index -= 1
            AnimalTypeA.LION.index -= 1
            AnimalTypeA.ELEPHANT.index -= 1
        }
        else if(victimRenderable == playeBcatRenderable){
            AnimalTypeA.DOG.index -= 1
            AnimalTypeA.WOLF.index -= 1
            AnimalTypeA.LEOPARD.index -= 1
            AnimalTypeA.TIGER.index -= 1
            AnimalTypeA.LION.index -= 1
            AnimalTypeA.ELEPHANT.index -= 1
        }
        else if(victimRenderable == playeBdogRenderable){
            AnimalTypeA.WOLF.index -= 1
            AnimalTypeA.LEOPARD.index -= 1
            AnimalTypeA.TIGER.index -= 1
            AnimalTypeA.LION.index -= 1
            AnimalTypeA.ELEPHANT.index -= 1
        }
        else if(victimRenderable == playeBwolveRenderable){
            AnimalTypeA.LEOPARD.index -= 1
            AnimalTypeA.TIGER.index -= 1
            AnimalTypeA.LION.index -= 1
            AnimalTypeA.ELEPHANT.index -= 1
        }
        else if(victimRenderable == playeBleopardRenderable){
            AnimalTypeA.TIGER.index -= 1
            AnimalTypeA.LION.index -= 1
            AnimalTypeA.ELEPHANT.index -= 1
        }
        else if(victimRenderable == playeBtigerRenderable){
            AnimalTypeA.LION.index -= 1
            AnimalTypeA.ELEPHANT.index -= 1
        }
        else if(victimRenderable == playeBlionRenderable){
            AnimalTypeA.ELEPHANT.index -= 1
        }

        placeChessmen(centerTile!!)
    }

    private fun moveChessman(newCol: Int, newRow: Int, renderable: ModelRenderable?, animalType: AnimalType, index: Int){

        for(node in centerTile!!.children){
            if(node.renderable == renderable){
                node.renderable = null
            }
        }

        val new = ChessmanNode(this,
            Animal(newCol, newRow, AnimalState.ALIVE, animalType),
            renderable!!)
        playeAChessmen[index] = new
        placeChessmen(centerTile!!)

        mGameController.updateGameInfo(Animal(newCol, newRow, AnimalState.ALIVE, animalType, AnimalDrawType.TYPE_A),
            Animal(newCol, newRow, AnimalState.ALIVE, animalType, AnimalDrawType.TYPE_A))
    }

    private fun moveChessmanB(newCol: Int, newRow: Int, renderable: ModelRenderable?, animalType: AnimalType, index: Int){

        for(node in centerTile!!.children){
            if(node.renderable == renderable){
                node.renderable = null
            }
        }

        val new = ChessmanNode(this,
            Animal(newCol, newRow, AnimalState.ALIVE, animalType),
            renderable!!)
        playeBChessmen[index] = new
        placeChessmen(centerTile!!)

        mGameController.updateGameInfo(Animal(newCol, newRow, AnimalState.ALIVE, animalType, AnimalDrawType.TYPE_B),
            Animal(newCol, newRow, AnimalState.ALIVE, animalType, AnimalDrawType.TYPE_B))
    }

    private fun getScreenCenter(): android.graphics.Point {
        val vw = findViewById<View>(android.R.id.content)
        return android.graphics.Point(vw.width / 2, vw.height / 2)
    }

    private fun onUpdate() {
        val trackingChanged = updateTracking()
        val contentView = findViewById<View>(android.R.id.content)
        if (trackingChanged) {
            if (isTracking) {
                contentView.overlay.add(pointer)
            } else {
                contentView.overlay.remove(pointer)
            }
            contentView.invalidate()
        }

        if (isTracking) {
            val hitTestChanged = updateHitTest()
            if (hitTestChanged) {
                pointer.setEnabled(isHitting)
                contentView.invalidate()
            }
        }
    }

    private fun updateTracking(): Boolean {
        val frame = arSceneView!!.getArFrame()
        val wasTracking = isTracking
        isTracking = frame.camera.trackingState == TrackingState.TRACKING
        return isTracking != wasTracking
    }


    private fun updateHitTest(): Boolean {
        val frame = arSceneView!!.getArFrame()
        val pt = getScreenCenter()
        val hits: List<HitResult>
        val wasHitting = isHitting
        isHitting = false
        if (frame != null) {
            hits = frame.hitTest(pt.x.toFloat(), pt.y.toFloat())
            for (hit in hits) {
                val trackable = hit.trackable
                if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                    Log.d(TAG, "HIT CAPTURE")
                    welcomeAnchor = hit.createAnchor()
                    //hideLoadingMessage()
                    showWelcomeMessage()
                    placeWelcomePanel()
                    needShowWelcomePanel = false
                    isHitting = true
                    break
                }
            }
        }
        return wasHitting != isHitting
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val result = Auth.GoogleSignInApi.getSignInResultFromIntent(data)
            if (result.isSuccess) {
                val account = result.signInAccount

                if (account != null) {
                    d(TAG, "Current user: ${account.displayName}, start authenticate")
                    val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                    firebaseAuthWithGoogle(credential)
                } else {
                    e(TAG, "Google signIn fail need retry")
                    Snackbar.make(findViewById(android.R.id.content), "Google signIn fail need retry", Snackbar.LENGTH_SHORT).show()
                }
            } else {
                e(TAG, "Google signIn fail need retry")
                Snackbar.make(findViewById(android.R.id.content), "Google signIn fail need retry", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        if (arSceneView!!.session == null) {
            // If the session wasn't created yet, don't resume rendering.
            // This can happen if ARCore needs to be updated or permissions are not granted yet.
            try {
                val session = Utils.createArSession(this, installRequested)
                if (session == null) {
                    installRequested = Utils.hasCameraPermission(this)
                    return
                } else {
                    arSession = session
                    arSceneView!!.setupSession(session)
                }
            } catch (e: UnavailableException) {
                Utils.handleSessionException(this, e)
            }

        }

        try {
            arSceneView!!.resume()
        } catch (ex: CameraNotAvailableException) {
            Utils.displayError(this, "Unable to get camera", ex)
            finish()
            return
        }

        if (arSceneView!!.session != null) {
            showLoadingMessage()
        }

    }

    public override fun onPause() {
        super.onPause()
        arSceneView!!.pause()
        mHandler.removeCallbacksAndMessages(null)
    }

    public override fun onDestroy() {
        super.onDestroy()
        arSceneView!!.destroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (!Utils.hasCameraPermission(this)) {
            if (!Utils.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                Utils.launchPermissionSettings(this)
            } else {
                Toast.makeText(
                    this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show()
            }
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Standard Android full-screen functionality.
            window
                .decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun onSingleTap(tap: MotionEvent) {
        if (welcomeAnchor != null) {
            d(TAG, "welcomeAnchor is still alive. destroy first.")
            return
        }

        if (!hasFinishedLoading) {
            return
        }
        val frame = arSceneView!!.arFrame
        if (frame != null) {
            if(mIsUserA){
                if (!hasPlacedTilesSystem && tryPlaceTile(tap, frame)) {
                    hasPlacedTilesSystem = true
                }
            }
            else{
                arSceneView!!
                    .scene
                    .setOnTouchListener { hitTestResult: HitTestResult, event: MotionEvent ->
                        if (hitTestResult.getNode() != null) {
                            //We have hit an AR node
                            Log.d(TAG, "hitTestResult.getNode() != null: " + hitTestResult.getNode());
                            val hitNode: Node? = hitTestResult.node
                            val rend: Renderable? = hitNode?.renderable

                            if(mCurrentGameState == GameState.USER_B_TURN && isGameStarted){

                                if(firstClick){
                                    if(rend == tilesRiverRenderable || rend == tilesGrassRenderable
                                        || rend == tilesABasementRenderable || rend == tilesBBasementRenderable
                                        || rend == tilesATrapRenderable || rend == tilesBTrapRenderable) {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Touched tile, click animal first!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        firstClick = true
                                    }

                                    else{
                                        firstClick = false
                                        for(chessman in playeBChessmen) {
                                            if (chessman.chessRenderable == rend) {
                                                animal = chessman.animal.animalType
                                                col = chessman.animal.posCol
                                                row = chessman.animal.posRow
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Touched " + animal.toString() + ", col: " + col.toString() + ", row: " + row.toString(),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                }

                                else{
                                    if(rend == tilesGrassRenderable || rend == tilesBTrapRenderable){
                                        val pos: String? = hitNode?.name
                                        val list: List<String>? = pos?.split("")
                                        if(list != null){
                                            val col2 = list[1].toInt()
                                            val row2 = list[2].toInt()

                                            if(((col2-col).absoluteValue == 1 && (row2-row).absoluteValue == 0) ||
                                                ((col2-col).absoluteValue == 0 && (row2-row).absoluteValue == 1)){

                                                if(animal == AnimalType.DOG){
                                                    eatOrMoveB(col2, row2, playeBdogRenderable, AnimalType.DOG, AnimalTypeB.DOG)
                                                }
                                                else if(animal == AnimalType.RAT){
                                                    eatOrMoveB(col2, row2, playeBmouseRenderable, AnimalType.RAT, AnimalTypeB.RAT)
                                                }
                                                else if(animal == AnimalType.CAT){
                                                    eatOrMoveB(col2, row2, playeBcatRenderable, AnimalType.CAT, AnimalTypeB.CAT)
                                                }
                                                else if(animal == AnimalType.WOLF){
                                                    eatOrMoveB(col2, row2, playeBwolveRenderable, AnimalType.WOLF, AnimalTypeB.WOLF)
                                                }
                                                else if(animal == AnimalType.LEOPARD){
                                                    eatOrMoveB(col2, row2, playeBleopardRenderable, AnimalType.LEOPARD, AnimalTypeB.LEOPARD)
                                                }
                                                else if(animal == AnimalType.TIGER){
                                                    eatOrMoveB(col2, row2, playeBtigerRenderable, AnimalType.TIGER, AnimalTypeB.TIGER)
                                                }
                                                else if(animal == AnimalType.LION){
                                                    eatOrMoveB(col2, row2, playeBlionRenderable, AnimalType.LION, AnimalTypeB.LION)
                                                }
                                                else if(animal == AnimalType.ELEPHANT){
                                                    eatOrMoveB(col2, row2, playeBelephantRenderable, AnimalType.ELEPHANT, AnimalTypeB.ELEPHANT)
                                                }

                                                mGameController.updateTurnInfo(true)
                                                startTimer(false)
                                            }
                                            else{
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Move is allowed in an horizontal or vertical square",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                    else if(rend == tilesRiverRenderable){
                                        val pos: String? = hitNode?.name
                                        val list: List<String>? = pos?.split("")
                                        if(list != null) {
                                            var col2 = list[1].toInt()
                                            var row2 = list[2].toInt()

                                            if(((col2-col).absoluteValue == 1 && (row2-row).absoluteValue == 0) ||
                                                ((col2-col).absoluteValue == 0 && (row2-row).absoluteValue == 1)){

                                                if(animal == AnimalType.RAT){
                                                    eatOrMoveB(col2, row2, playeBmouseRenderable, AnimalType.RAT, AnimalTypeB.RAT)

                                                    mGameController.updateTurnInfo(true)
                                                    startTimer(false)
                                                }

                                                else if(animal == AnimalType.LION){
                                                    if((col2-col).absoluteValue == 1 && (row2-row).absoluteValue == 0){
                                                        if(col2 < col){
                                                            col2 -= 2
                                                        }
                                                        else{
                                                            col2 += 2
                                                        }
                                                    }
                                                    else{
                                                        if(row2 < row){
                                                            row2 -= 3
                                                        }
                                                        else{
                                                            row2 += 3
                                                        }
                                                    }
                                                    eatOrMoveB(col2, row2, playeBlionRenderable, AnimalType.LION, AnimalTypeB.LION)

                                                    mGameController.updateTurnInfo(true)
                                                    startTimer(false)
                                                }
                                                else if(animal == AnimalType.TIGER){
                                                    if((col2-col).absoluteValue == 1 && (row2-row).absoluteValue == 0){
                                                        if(col2 < col){
                                                            col2 -= 2
                                                        }
                                                        else{
                                                            col2 += 2
                                                        }
                                                    }
                                                    else{
                                                        if(row2 < row){
                                                            row2 -= 3
                                                        }
                                                        else{
                                                            row2 += 3
                                                        }
                                                    }
                                                    eatOrMoveB(col2, row2, playeBtigerRenderable, AnimalType.TIGER, AnimalTypeB.TIGER)

                                                    mGameController.updateTurnInfo(true)
                                                    startTimer(false)
                                                }
                                                else{
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "This animal cannot go on the river",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }

                                            }
                                        }
                                    }
                                    else if(rend == playeBdogRenderable || rend == playeBmouseRenderable || rend == playeBcatRenderable ||
                                        rend == playeBelephantRenderable || rend == playeBleopardRenderable || rend == playeBlionRenderable ||
                                        rend == playeBwolveRenderable || rend == playeBtigerRenderable){

                                        Toast.makeText(
                                            this@MainActivity,
                                            "Square already occupied by an animal of yours",
                                            Toast.LENGTH_SHORT
                                        ).show()

                                    }
                                    else if(rend == tilesBBasementRenderable){
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Cannot occupy your castle",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    else if(rend == tilesATrapRenderable){
                                        val pos: String? = hitNode?.name
                                        val list: List<String>? = pos?.split("")
                                        if(list != null) {
                                            val col2 = list[1].toInt()
                                            val row2 = list[2].toInt()

                                            if (((col2 - col).absoluteValue == 1 && (row2 - row).absoluteValue == 0) ||
                                                ((col2 - col).absoluteValue == 0 && (row2 - row).absoluteValue == 1)
                                            ) {
                                                if(animal == AnimalType.DOG){
                                                    eatOrMoveTrapB(col2, row2, playeBdogRenderable, AnimalType.DOG, AnimalTypeB.DOG)
                                                }
                                                else if(animal == AnimalType.RAT){
                                                    eatOrMoveTrapB(col2, row2, playeBmouseRenderable, AnimalType.RAT, AnimalTypeB.RAT)
                                                }
                                                else if(animal == AnimalType.CAT){
                                                    eatOrMoveTrapB(col2, row2, playeBcatRenderable, AnimalType.CAT, AnimalTypeB.CAT)
                                                }
                                                else if(animal == AnimalType.WOLF){
                                                    eatOrMoveTrapB(col2, row2, playeBwolveRenderable, AnimalType.WOLF, AnimalTypeB.WOLF)
                                                }
                                                else if(animal == AnimalType.LEOPARD){
                                                    eatOrMoveTrapB(col2, row2, playeBleopardRenderable, AnimalType.LEOPARD, AnimalTypeB.LEOPARD)
                                                }
                                                else if(animal == AnimalType.TIGER){
                                                    eatOrMoveTrapB(col2, row2, playeBtigerRenderable, AnimalType.TIGER, AnimalTypeB.TIGER)
                                                }
                                                else if(animal == AnimalType.LION){
                                                    eatOrMoveTrapB(col2, row2, playeBlionRenderable, AnimalType.LION, AnimalTypeB.LION)
                                                }
                                                else if(animal == AnimalType.ELEPHANT){
                                                    eatOrMoveTrapB(col2, row2, playeBelephantRenderable, AnimalType.ELEPHANT, AnimalTypeB.ELEPHANT)
                                                }

                                                mGameController.updateTurnInfo(true)
                                                startTimer(false)
                                            }
                                        }
                                    }
                                    else if(rend == playeAdogRenderable || rend == playeAmouseRenderable || rend == playeAcatRenderable ||
                                        rend == playeAelephantRenderable || rend == playeAleopardRenderable || rend == playeAlionRenderable ||
                                        rend == playeAwolveRenderable || rend == playeAtigerRenderable){

                                        var col2 = 0
                                        var row2 = 0
                                        for(chessman in playeBChessmen){
                                            if(chessman.chessRenderable == rend){
                                                col2 = chessman.animal.posCol
                                                row2 = chessman.animal.posRow
                                            }
                                        }

                                        if(((col2-col).absoluteValue == 1 && (row2-row).absoluteValue == 0) ||
                                            ((col2-col).absoluteValue == 0 && (row2-row).absoluteValue == 1)){

                                            if(animal == AnimalType.DOG){
                                                eatOrMoveB(col2, row2, playeBdogRenderable, AnimalType.DOG, AnimalTypeB.DOG)
                                            }
                                            else if(animal == AnimalType.RAT){
                                                eatOrMoveB(col2, row2, playeBmouseRenderable, AnimalType.RAT, AnimalTypeB.RAT)
                                            }
                                            else if(animal == AnimalType.CAT){
                                                eatOrMoveB(col2, row2, playeBcatRenderable, AnimalType.CAT, AnimalTypeB.CAT)
                                            }
                                            else if(animal == AnimalType.WOLF){
                                                eatOrMoveB(col2, row2, playeBwolveRenderable, AnimalType.WOLF, AnimalTypeB.WOLF)
                                            }
                                            else if(animal == AnimalType.LEOPARD){
                                                eatOrMoveB(col2, row2, playeBleopardRenderable, AnimalType.LEOPARD, AnimalTypeB.LEOPARD)
                                            }
                                            else if(animal == AnimalType.TIGER){
                                                eatOrMoveB(col2, row2, playeBtigerRenderable, AnimalType.TIGER, AnimalTypeB.TIGER)
                                            }
                                            else if(animal == AnimalType.LION){
                                                eatOrMoveB(col2, row2, playeBlionRenderable, AnimalType.LION, AnimalTypeB.LION)
                                            }
                                            else if(animal == AnimalType.ELEPHANT){
                                                eatOrMoveB(col2, row2, playeBelephantRenderable, AnimalType.ELEPHANT, AnimalTypeB.ELEPHANT)
                                            }

                                            mGameController.updateTurnInfo(true)
                                            startTimer(false)
                                        }
                                    }
                                    else if(rend == tilesABasementRenderable){
                                        val pos: String? = hitNode?.name
                                        val list: List<String>? = pos?.split("")
                                        if(list != null) {
                                            val col2 = list[1].toInt()
                                            val row2 = list[2].toInt()

                                            if (((col2 - col).absoluteValue == 1 && (row2 - row).absoluteValue == 0) ||
                                                ((col2 - col).absoluteValue == 0 && (row2 - row).absoluteValue == 1)
                                            ) {
                                                mGameController.updateGameFinish(false)
                                            }
                                        }

                                    }
                                    firstClick = true
                                }

                            }
                        }
                        false
                    }
            }
        }
    }


    private fun tryPlaceTile(tap: MotionEvent?, frame: Frame): Boolean {

        if (cloudAnchor != null) {
            d(TAG, "Already had cloudAnchor, no need to host again.")
            return false
        }

        if (tap != null && frame.camera.trackingState == TrackingState.TRACKING) {
            for (hit in frame.hitTest(tap)) {
                Log.d(TAG, "capture Hit")
                val trackable = hit.trackable
                if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                    // Create the Anchor.
                    val anchor = hit.createAnchor()
                    hostCloudAnchor(anchor)
                }
            }
        }
        return true
    }

    private fun initTilesAndChessmen(): Node {
        val base = Node()
        centerTile = Node()
        centerTile!!.setParent(base)
        centerTile!!.localPosition = Vector3(0.0f, 0.0f, 0.0f)
        centerTile!!.renderable = tilesRiverRenderable
        initNeighbourTiles(centerTile!!)
        initChessmen(centerTile!!)
        initControllerPanel(centerTile!!)
        return base
    }


    private fun initControllerPanel(center: Node) {
        controllerNode = Node()
        controllerNode.renderable = controllerRenderable
        controllerNode.localPosition = Vector3(0f, 0.5f, 0f)
        val controllerRenderableView = controllerRenderable!!.view
        val p1_name = controllerRenderableView.findViewById<TextView>(R.id.p1_name)
        val p1_photo = controllerRenderableView.findViewById<ImageView>(R.id.p1_photo)
        if (mFirebaseUser != null) {
            p1_name.text = mFirebaseUser!!.displayName
            DownloadImageTask(p1_photo).execute(mFirebaseUser!!.photoUrl.toString())
        }
        controllerNode.setParent(center)
    }

    private fun updateControllerPanel(otherUserInfo: ChessUserInfo) {
        val controllerRenderableView = controllerRenderable!!.view
        val p2_name = controllerRenderableView.findViewById<TextView>(R.id.p2_name)
        val p2_photo = controllerRenderableView.findViewById<ImageView>(R.id.p2_photo)

        p2_name.text = otherUserInfo.displayName
        DownloadImageTask(p2_photo).execute("https://lh6.googleusercontent.com" + otherUserInfo.photoUrl)
        // controllerNode.renderable = controllerRenderable

        val ll_start_game = controllerRenderableView.findViewById<LinearLayout>(R.id.ll_start_game)
        val btn_start_game = controllerRenderableView.findViewById<Button>(R.id.btn_start_game)
        btn_start_game.setOnClickListener{
            btn_start_game.text = "Waiting for "+ otherUserInfo.displayName
            gameController.confirmGameStart{ _, _ ->
                ll_start_game.visibility = GONE
                initTimingPanel()
                isGameStarted = true
            }
        }
        ll_start_game.visibility = VISIBLE
    }

    private fun initTimingPanel(){
        val controllerRenderableView = controllerRenderable!!.view
        val rl_time_board = controllerRenderableView.findViewById<RelativeLayout>(R.id.rl_time_board)
        val tv_turn = controllerRenderableView.findViewById<TextView>(R.id.tv_turn)
        val tv_time = controllerRenderableView.findViewById<TextView>(R.id.tv_time)

        if(mCurrentGameState == GameState.USER_A_TURN){
            if(mIsUserA){
                tv_turn.text = "Your turn"
            }
            else{
                tv_turn.text = "Opponent turn"
            }
        }
        else if(mCurrentGameState == GameState.USER_B_TURN){
            if(mIsUserA){
                tv_turn.text = "Opponent turn"
            }
            else{
                tv_turn.text = "Your turn"
            }
        }

        countDownTimer = object : CountDownTimer(30 * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                var secs =  millisUntilFinished/1000
                tv_time.text = "Time Remaining: 00:$secs"
            }

            override fun onFinish() {
                tv_time.text = "Time Remaining: : 00:00"
                if(mCurrentGameState == GameState.USER_A_TURN){
                    mGameController.updateTurnInfo(false)
                }
                else if(mCurrentGameState == GameState.USER_B_TURN){
                    mGameController.updateTurnInfo(true)
                }
                //cancel()
                countDownTimer!!.start()
            }
        }
        rl_time_board.visibility = VISIBLE
        (countDownTimer as CountDownTimer).start()
    }

    private fun initChessmen(centerTile: Node) {
        val tigerA = ChessmanNode(this,
            Animal(0, 8, AnimalState.ALIVE, AnimalType.TIGER),
            playeAtigerRenderable!!)
        val lionA = ChessmanNode(this,
            Animal(6, 8, AnimalState.ALIVE, AnimalType.LION),
            playeAlionRenderable!!)

        val catA = ChessmanNode(this,
            Animal(1, 7, AnimalState.ALIVE, AnimalType.CAT),
            playeAcatRenderable!!)
        val dogA = ChessmanNode(this,
            Animal(5, 7, AnimalState.ALIVE, AnimalType.DOG),
            playeAdogRenderable!!)

        val elephantA: ChessmanNode = ChessmanNode(this,
            Animal(0, 6, AnimalState.ALIVE, AnimalType.ELEPHANT),
            playeAelephantRenderable!!)
        val wolfA = ChessmanNode(this,
            Animal(2, 6, AnimalState.ALIVE, AnimalType.WOLF),
            playeAwolveRenderable!!)
        val leopardA = ChessmanNode(this,
            Animal(4, 6, AnimalState.ALIVE, AnimalType.LEOPARD),
            playeAleopardRenderable!!)
        val mouseA = ChessmanNode(this,
            Animal(6, 6, AnimalState.ALIVE, AnimalType.RAT),
            playeAmouseRenderable!!)

        val chessmanArrayA = arrayOf(mouseA, catA, dogA, wolfA, leopardA, tigerA, lionA, elephantA)
        playeAChessmen = LinkedList(Arrays.asList(*chessmanArrayA))

        val tigerB = ChessmanNode(this,
            Animal(6, 0, AnimalState.ALIVE, AnimalType.TIGER),
            playeBtigerRenderable!!)
        val lionB = ChessmanNode(this,
            Animal(0, 0, AnimalState.ALIVE, AnimalType.LION),
            playeBlionRenderable!!)

        val catB = ChessmanNode(this,
            Animal(5, 1, AnimalState.ALIVE, AnimalType.CAT),
            playeBcatRenderable!!)
        val dogB = ChessmanNode(this,
            Animal(1, 1, AnimalState.ALIVE, AnimalType.DOG),
            playeBdogRenderable!!)

        val mouseB = ChessmanNode(this,
            Animal(0, 2, AnimalState.ALIVE, AnimalType.RAT),
            playeBmouseRenderable!!)
        val leopardB = ChessmanNode(this,
            Animal(2, 2, AnimalState.ALIVE, AnimalType.LEOPARD),
            playeBleopardRenderable!!)
        val wolfB: ChessmanNode = ChessmanNode(this,
            Animal(4, 2, AnimalState.ALIVE, AnimalType.WOLF),
            playeBwolveRenderable!!)
        val elephantB = ChessmanNode(this,
            Animal(6, 2, AnimalState.ALIVE, AnimalType.ELEPHANT),
            playeBelephantRenderable!!)

        val chessmanArrayB = arrayOf(mouseB, catB, dogB, wolfB, leopardB, tigerB, lionB, elephantB)
        playeBChessmen = LinkedList(Arrays.asList(*chessmanArrayB))

        placeChessmen(centerTile)
    }

    private fun initNeighbourTiles(center: Node) {
        var name: String
        var column: Int
        var row1: Int
        var tile: TileNode
        var tile2: TileNode
        var chessmanA: ChessmanNode
        var chessmanB: ChessmanNode
        var distanceToCenter: Double
        var test: String

        for (row in 0..8) {
            for (col in 0..6) {
                name = col.toString() + row.toString()
                distanceToCenter = Math.sqrt(Math.pow((row - 4).toDouble(), 2.0) + Math.pow((col - 3).toDouble(), 2.0))
                /*
                Place splitters
                 */
                val splitColNode = Node()
                val splitRowNode = Node()
                splitColNode.renderable = tilesSplierator
                splitRowNode.renderable = tilesSplierator
                splitColNode.localRotation = Quaternion.axisAngle(Vector3(0.0f, 1.0f, 0.0f), 90f)
                splitRowNode.localPosition = Vector3((col - 3).toFloat() / 8, 0F, (row - 3.5).toFloat() / 8)
                splitColNode.localPosition = Vector3((col - 2.625).toFloat() / 8, 0F, (row - 4).toFloat() / 8)
                splitRowNode.setParent(center)
                splitColNode.setParent(center)
                /*
               Place tiles
                */
                if (row == 0 && col == 3) {

                    tile = TileNode(this, distanceToCenter.toFloat(), Tile(tileType = TileType.TILE_BASEMENT), tilesBBasementRenderable!!)
                    tile.localPosition = Vector3((col - 3).toFloat() / 8, 0.05F, (row - 4).toFloat() / 8)
                    tile.localRotation = Quaternion.axisAngle(Vector3(0.0f, 1.0f, 0.0f), 90f)
                    tile.renderable = tilesBBasementRenderable
                    tile.name = name
                } else if (row == 8 && col == 3) {
                    tile = TileNode(this, distanceToCenter.toFloat(), Tile(tileType = TileType.TILE_BASEMENT), tilesABasementRenderable!!)
                    tile.localPosition = Vector3((col - 3).toFloat() / 8, 0.05F, (row - 4).toFloat() / 8)
                    tile.localRotation = Quaternion.axisAngle(Vector3(0.0f, 1.0f, 0.0f), 270f)
                    tile.renderable = tilesABasementRenderable
                    tile.name = name
                } else if ((col == 2 && row == 0) ||
                    (col == 3 && row == 1) ||
                    (col == 4 && row == 0)) {
                    tile = TileNode(this, distanceToCenter.toFloat(), Tile(tileType = TileType.TILE_TRAP), tilesBTrapRenderable!!)
                    tile.renderable = tilesBTrapRenderable
                    tile.localScale = Vector3(1f, 0.2f, 1f)
                    tile.localPosition = Vector3((col - 3).toFloat() / 8, 0F, (row - 4).toFloat() / 8)
                    tile.name = name
                } else if((col == 2 && row == 8) ||
                    (col == 3 && row == 7) ||
                    (col == 4 && row == 8)){
                    tile = TileNode(this, distanceToCenter.toFloat(), Tile(tileType = TileType.TILE_TRAP), tilesATrapRenderable!!)
                    tile.renderable = tilesATrapRenderable
                    tile.localScale = Vector3(1f, 0.2f, 1f)
                    tile.localPosition = Vector3((col - 3).toFloat() / 8, 0F, (row - 4).toFloat() / 8)
                    tile.name = name
                } else if ((row == 3 && (col == 1 || col == 2 || col == 4 || col == 5)) ||
                    (row == 4 && (col == 1 || col == 2 || col == 4 || col == 5)) ||
                    (row == 5 && (col == 1 || col == 2 || col == 4 || col == 5))) {
                    tile = TileNode(this, distanceToCenter.toFloat(), Tile(tileType = TileType.TILE_RIVER), tilesRiverRenderable!!)
                    tile.renderable = tilesRiverRenderable
                    tile.localPosition = Vector3((col - 3).toFloat() / 8, 0F, (row - 4).toFloat() / 8)
                    tile.name = name
                } else {
                    tile = TileNode(this, distanceToCenter.toFloat(), Tile(tileType = TileType.TILE_GRASS), tilesGrassRenderable!!)
                    tile.renderable = tilesGrassRenderable
                    tile.localPosition = Vector3((col - 3).toFloat() / 8, 0F, (row - 4).toFloat() / 8)
                    tile.name = name
                }
                tile.setParent(center)
            }
        }
    }

    private fun showLoadingMessage() {
        toolbar!!.title = "AR Jungle Chess"
    }

    private fun showWelcomeMessage() {
        toolbar!!.visibility = VISIBLE
        toolbar!!.title = "AR Jungle Chess"
    }

    /*private fun hideLoadingMessage() {
        toolbar!!.visibility = View.GONE
    }*/

    private fun setNewAnchor(newAnchor: Anchor) {
        if (cloudAnchor != null) {
            cloudAnchor!!.detach()
        }
        cloudAnchor = newAnchor
        appAnchorState = AppAnchorState.NONE
    }

    private fun placeChessmen(centerTile: Node) {
        for (chessmanNode in playeAChessmen) {
            val col = chessmanNode.animal.posCol
            val row = chessmanNode.animal.posRow
            chessmanNode.localPosition = Vector3((col - 3).toFloat() / 8, 0.05F, (row - 4).toFloat() / 8)
            chessmanNode.localRotation = Quaternion.axisAngle(Vector3(0.0f, 1.0f, 0.0f), 180f)
            chessmanNode.setParent(centerTile)
        }

        for (chessmanNode in playeBChessmen) {
            val col = chessmanNode.animal.posCol
            val row = chessmanNode.animal.posRow
            chessmanNode.localPosition = Vector3((col - 3).toFloat() / 8, 0.05F, (row - 4).toFloat() / 8)
            chessmanNode.setParent(centerTile)
        }
    }

    private fun placeWelcomePanel() {
        welcomeNode.renderable = welcomeRenderable
        welcomeNode.localPosition = Vector3(0.0f, 0f, 0.0f)

        val welcomeRenderableView = welcomeRenderable!!.view
        val btn_new_game = welcomeRenderableView.findViewById<Button>(R.id.btn_new_game)
        val btn_pair = welcomeRenderableView.findViewById<Button>(R.id.btn_pair)
        btn_new_game.setOnClickListener {
            mIsUserA = true
            signInGoogleAccount()
            welcomeAnchor!!.detach()
            welcomeAnchor = null

        }

        btn_pair.setOnClickListener {
            mIsUserA = false
            signInGoogleAccount()
            welcomeAnchor!!.detach()
            welcomeAnchor = null

        }

        val anchorNode = AnchorNode(welcomeAnchor)
        anchorNode.setParent(arSceneView!!.scene)
        anchorNode.addChild(welcomeNode)
    }

    private fun placeBoard() {
        val anchorNode = AnchorNode(cloudAnchor)
        anchorNode.setParent(arSceneView!!.scene)

        val tilesAndChessmen = initTilesAndChessmen()
        anchorNode.addChild(tilesAndChessmen)
    }



    private fun signInGoogleAccount() {
        if (mFirebaseUser != null) {
            welcomeUserAndStoreUserInfo()
        } else {
            //Sign In
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            mGoogleApiClient = GoogleApiClient.Builder(this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build()
            val signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient)
            startActivityForResult(
                signInIntent,
                RC_SIGN_IN)
        }
    }


    private fun firebaseAuthWithGoogle(credential: AuthCredential) {
        d(TAG, "firebaseAuthWithGoogle: ${credential.provider}")

        mFirebaseAuth
            ?.signInWithCredential(credential)
            ?.addOnCompleteListener(this) {
                d(TAG, "signInWithCredential: ${it.isSuccessful}")
                if (!it.isSuccessful) {
                    e(TAG, "signInWithCredential fail need retry")
                } else {
                    mFirebaseUser = mFirebaseAuth!!.currentUser
                    welcomeUserAndStoreUserInfo()
                }
            }
    }


    private fun welcomeUserAndStoreUserInfo() {
        if (mIsUserA) {
            Snackbar.make(findViewById(android.R.id.content),
                "Welcome! Current user: ${mFirebaseUser!!.displayName}, Please place the board and create room.",
                Snackbar.LENGTH_SHORT).show()
        } else {
            Snackbar.make(findViewById(android.R.id.content),
                "Welcome! Current user: ${mFirebaseUser!!.displayName}, Please input room number to pair into game.",
                Snackbar.LENGTH_SHORT).show()

            showResolveAnchorPanel()
        }
    }

    private fun onAnimalMoveUpdate(updatedAnimalA: Animal){
        val col = updatedAnimalA.posCol
        val row = updatedAnimalA.posRow

        if(updatedAnimalA.animalDrawType == AnimalDrawType.TYPE_A){
            if(updatedAnimalA.animalType == AnimalType.RAT){
                eatOrMove(col, row, playeAmouseRenderable, AnimalType.RAT, AnimalTypeA.RAT)
                //moveChessman(col, row, playeAmouseRenderable, AnimalType.RAT, AnimalType.RAT.index-1)
            }
            else if(updatedAnimalA.animalType == AnimalType.CAT){
                eatOrMove(col, row, playeAcatRenderable, AnimalType.CAT, AnimalTypeA.CAT)
                //moveChessman(col, row, playeAcatRenderable, AnimalType.CAT, AnimalType.CAT.index-1)
            }
            else if(updatedAnimalA.animalType == AnimalType.DOG){
                eatOrMove(col, row, playeAdogRenderable, AnimalType.DOG, AnimalTypeA.DOG)
                //moveChessman(col, row, playeAdogRenderable, AnimalType.DOG, AnimalType.DOG.index-1)
            }
            else if(updatedAnimalA.animalType == AnimalType.WOLF){
                eatOrMove(col, row, playeAwolveRenderable, AnimalType.WOLF, AnimalTypeA.WOLF)
                //moveChessman(col, row, playeAwolveRenderable, AnimalType.WOLF, AnimalType.WOLF.index-1)
            }
            else if(updatedAnimalA.animalType == AnimalType.LEOPARD){
                eatOrMove(col, row, playeAleopardRenderable, AnimalType.LEOPARD, AnimalTypeA.LEOPARD)
                //moveChessman(col, row, playeAleopardRenderable, AnimalType.LEOPARD, AnimalType.LEOPARD.index-1)
            }
            else if(updatedAnimalA.animalType == AnimalType.TIGER){
                eatOrMove(col, row, playeAtigerRenderable, AnimalType.TIGER, AnimalTypeA.TIGER)
                //moveChessman(col, row, playeAtigerRenderable, AnimalType.TIGER, AnimalType.TIGER.index-1)
            }
            else if(updatedAnimalA.animalType == AnimalType.LION){
                eatOrMove(col, row, playeAlionRenderable, AnimalType.LION, AnimalTypeA.LION)
                //moveChessman(col, row, playeAlionRenderable, AnimalType.LION, AnimalType.LION.index-1)
            }
            else if(updatedAnimalA.animalType == AnimalType.ELEPHANT){
                eatOrMove(col, row, playeAelephantRenderable, AnimalType.ELEPHANT, AnimalTypeA.ELEPHANT)
                //moveChessman(col, row, playeAelephantRenderable, AnimalType.ELEPHANT, AnimalType.ELEPHANT.index-1)
            }

            endTimer(true)
        }

        else{
            if(updatedAnimalA.animalType == AnimalType.RAT){
                eatOrMoveB(col, row, playeBmouseRenderable, AnimalType.RAT, AnimalTypeB.RAT)
                //moveChessmanB(col, row, playeBmouseRenderable, AnimalType.RAT, AnimalType.RAT.index-1)
            }
            else if(updatedAnimalA.animalType == AnimalType.CAT){
                eatOrMoveB(col, row, playeBcatRenderable, AnimalType.CAT, AnimalTypeB.CAT)
                //moveChessmanB(col, row, playeBcatRenderable, AnimalType.CAT, AnimalType.CAT.index-1)
            }
            else if(updatedAnimalA.animalType == AnimalType.DOG){
                eatOrMoveB(col, row, playeBdogRenderable, AnimalType.DOG, AnimalTypeB.DOG)
                //moveChessmanB(col, row, playeBdogRenderable, AnimalType.DOG, AnimalType.DOG.index-1)
            }
            else if(updatedAnimalA.animalType == AnimalType.WOLF){
                eatOrMoveB(col, row, playeBwolveRenderable, AnimalType.WOLF, AnimalTypeB.WOLF)
                //moveChessmanB(col, row, playeBwolveRenderable, AnimalType.WOLF, AnimalType.WOLF.index-1)
            }
            else if(updatedAnimalA.animalType == AnimalType.LEOPARD){
                eatOrMoveB(col, row, playeBleopardRenderable, AnimalType.LEOPARD, AnimalTypeB.LEOPARD)
                //moveChessmanB(col, row, playeBleopardRenderable, AnimalType.LEOPARD, AnimalType.LEOPARD.index-1)
            }
            else if(updatedAnimalA.animalType == AnimalType.TIGER){
                eatOrMoveB(col, row, playeBtigerRenderable, AnimalType.TIGER, AnimalTypeB.TIGER)
                //moveChessmanB(col, row, playeBtigerRenderable, AnimalType.TIGER, AnimalType.TIGER.index-1)
            }
            else if(updatedAnimalA.animalType == AnimalType.LION){
                eatOrMoveB(col, row, playeBlionRenderable, AnimalType.LION, AnimalTypeB.LION)
                //moveChessmanB(col, row, playeBlionRenderable, AnimalType.LION, AnimalType.LION.index-1)
            }
            else if(updatedAnimalA.animalType == AnimalType.ELEPHANT){
                eatOrMoveB(col, row, playeBelephantRenderable, AnimalType.ELEPHANT, AnimalTypeB.ELEPHANT)
                //moveChessmanB(col, row, playeBelephantRenderable, AnimalType.ELEPHANT, AnimalType.ELEPHANT.index-1)
            }

            endTimer(false)
        }
    }

    private fun onAnimalEatUpdate(updatedAnimalA: Animal){
        if(updatedAnimalA.animalDrawType == AnimalDrawType.TYPE_A){
            if(updatedAnimalA.animalType == AnimalType.RAT) {
                removeChessmanA(playeAmouseRenderable, AnimalTypeA.RAT.index - 1)
            }
            else if(updatedAnimalA.animalType == AnimalType.CAT){
                removeChessmanA(playeAcatRenderable, AnimalTypeA.CAT.index - 1)
            }
            else if(updatedAnimalA.animalType == AnimalType.DOG){
                removeChessmanA(playeAdogRenderable, AnimalTypeA.DOG.index - 1)
            }
            else if(updatedAnimalA.animalType == AnimalType.WOLF){
                removeChessmanA(playeAwolveRenderable, AnimalTypeA.WOLF.index - 1)
            }
            else if(updatedAnimalA.animalType == AnimalType.LEOPARD){
                removeChessmanA(playeAleopardRenderable, AnimalTypeA.LEOPARD.index - 1)
            }
            else if(updatedAnimalA.animalType == AnimalType.TIGER){
                removeChessmanA(playeAtigerRenderable, AnimalTypeA.TIGER.index - 1)
            }
            else if(updatedAnimalA.animalType == AnimalType.LION){
                removeChessmanA(playeAlionRenderable, AnimalTypeA.LION.index - 1)
            }
            else if(updatedAnimalA.animalType == AnimalType.ELEPHANT){
                removeChessmanA(playeAelephantRenderable, AnimalTypeA.ELEPHANT.index - 1)
            }
        }

        else{
            if(updatedAnimalA.animalType == AnimalType.RAT) {
                removeChessmanB(playeBmouseRenderable, AnimalTypeB.RAT.index - 1)
            }
            else if(updatedAnimalA.animalType == AnimalType.CAT){
                removeChessmanB(playeBcatRenderable, AnimalTypeB.CAT.index - 1)
            }
            else if(updatedAnimalA.animalType == AnimalType.DOG){
                removeChessmanB(playeBdogRenderable, AnimalTypeB.DOG.index - 1)
            }
            else if(updatedAnimalA.animalType == AnimalType.WOLF){
                removeChessmanB(playeBwolveRenderable, AnimalTypeB.WOLF.index - 1)
            }
            else if(updatedAnimalA.animalType == AnimalType.LEOPARD){
                removeChessmanB(playeBleopardRenderable, AnimalTypeB.LEOPARD.index - 1)
            }
            else if(updatedAnimalA.animalType == AnimalType.TIGER){
                removeChessmanB(playeBtigerRenderable, AnimalTypeB.TIGER.index - 1)
            }
            else if(updatedAnimalA.animalType == AnimalType.LION){
                removeChessmanB(playeBlionRenderable, AnimalTypeB.LION.index - 1)
            }
            else if(updatedAnimalA.animalType == AnimalType.ELEPHANT){
                removeChessmanB(playeBelephantRenderable, AnimalTypeB.ELEPHANT.index - 1)
            }
        }

    }


    private fun onUserTurn(userA: Boolean){
        d(TAG, "onUserTurn, userATurn: $userA")

        if(userA){
            mCurrentGameState = GameState.USER_A_TURN
        }
        else{
            mCurrentGameState = GameState.USER_B_TURN
        }

        val controllerRenderableView = controllerRenderable!!.view
        val tv_turn = controllerRenderableView.findViewById<TextView>(R.id.tv_turn)

        if(mCurrentGameState == GameState.USER_A_TURN){
            if(mIsUserA){
                tv_turn.text = "Your turn"
            }
            else{
                tv_turn.text = "Opponent turn"
            }
        }
        else if(mCurrentGameState == GameState.USER_B_TURN){
            if(mIsUserA){
                tv_turn.text = "Opponent turn"
            }
            else{
                tv_turn.text = "Your turn"
            }
        }

        countDownTimer!!.cancel()
        countDownTimer!!.start()

        Toast.makeText(this@MainActivity,
            "Turn: " + mCurrentGameState.toString(),
            Toast.LENGTH_SHORT).show()
    }

    private fun onGameFinish(gameState: GameState, currentRound: Int) {
        val controllerRenderableView = controllerRenderable!!.view
        val rl_time_board = controllerRenderableView.findViewById<RelativeLayout>(R.id.rl_time_board)
        val ll_start_game = controllerRenderableView.findViewById<LinearLayout>(R.id.ll_start_game)
        val btn_start_game = controllerRenderableView.findViewById<Button>(R.id.btn_start_game)
        val p1_name = controllerRenderableView.findViewById<TextView>(R.id.p1_name).text
        val p2_name = controllerRenderableView.findViewById<TextView>(R.id.p2_name).text

        rl_time_board.visibility = GONE

        if(gameState == GameState.USER_A_WIN){
            if(mIsUserA){
                btn_start_game.text = "" + p1_name + " wins in " + currentRound.toString() + " moves!"
            }
            else{
                btn_start_game.text = "" + p2_name + " wins in " + currentRound.toString() + " moves!"
            }
        }
        else{
            if(mIsUserA){
                btn_start_game.text = "" + p2_name + " wins in " + currentRound.toString() + " moves!"
            }
            else{
                btn_start_game.text = "" + p1_name + " wins in " + currentRound.toString() + " moves!"
            }
        }

        isGameStarted = false

        ll_start_game.visibility = VISIBLE
    }

    private fun onReadUserInfo(currentUserInfo: ChessUserInfo, otherUserInfo: ChessUserInfo) {
        d(TAG, "currentUserInfo: $currentUserInfo")
        d(TAG, "otherUserInfo: $otherUserInfo")
        updateControllerPanel(otherUserInfo)
    }

    private fun hostCloudAnchor(anchor: Anchor) {
        val session = arSceneView!!.session

        val newAnchor = session.hostCloudAnchor(anchor)
        setNewAnchor(newAnchor)

        startCheckUpdatedAnchor()

        placeBoard()
        Snackbar.make(findViewById(android.R.id.content), "Hosting Cloud Anchor ...", Snackbar.LENGTH_SHORT).show()
        d(TAG, "setNewAnchor: hostCloudAnchor HOSTING")
        appAnchorState = AppAnchorState.HOSTING
    }

    private fun showResolveAnchorPanel() {
        if (cloudAnchor != null) {
            e(TAG, "Already had cloud anchor, need clear anchor first.")
            return
        }
        val dialogFragment = ResolveDialogFragment()
        dialogFragment.setOkListener(this::onResolveOkPressed)
        dialogFragment.showNow(supportFragmentManager, "Resolve")
    }

    private fun onResolveOkPressed(dialogValue: String) {
        val roomId = dialogValue.toInt()
        mGameController.pairGame(roomId) { cloudAnchorId ->
            mGameController.storeUserInfo(mIsUserA, mFirebaseUser!!.uid, mFirebaseUser!!.displayName, mFirebaseUser!!.photoUrl!!.path)
            if (arSession == null) {
                e(TAG, "onResolveOkPressed failed due to arSession is null")
            } else {
                val resolveAnchor = arSession!!.resolveCloudAnchor(cloudAnchorId)
                setNewAnchor(resolveAnchor)
                startCheckUpdatedAnchor()
                d(TAG, "onResolveOkPressed: resolving anchor")
                appAnchorState = AppAnchorState.RESOLVING
            }
        }
    }

    private fun startCheckUpdatedAnchor() {
        d(TAG, "startCheckUpdatedAnchor")
        mHandler.removeCallbacksAndMessages(null)
        mHandler.postDelayed(mCheckAnchorUpdateRunnable, 2000)
    }

    private fun checkUpdatedAnchor() {
        var room: String? = null
        if (appAnchorState != AppAnchorState.HOSTING && appAnchorState != AppAnchorState.RESOLVING || cloudAnchor == null)
            return
        val cloudState = cloudAnchor!!.cloudAnchorState

        if (appAnchorState == AppAnchorState.HOSTING) {
            if (cloudState.isError) {
                mHandler.removeCallbacksAndMessages(null)
                appAnchorState = AppAnchorState.NONE
                Snackbar.make(findViewById(android.R.id.content), "Anchor hosted error:  state: $cloudState", Snackbar.LENGTH_SHORT).show()
                e(TAG, "Anchor hosted error:  CloudId: $cloudState")
            } else if (cloudState == Anchor.CloudAnchorState.SUCCESS) {
                appAnchorState = AppAnchorState.HOSTED
                mHandler.removeCallbacksAndMessages(null)
                mGameController.initGame(cloudAnchor!!.cloudAnchorId) { roomId ->
                    if (roomId == null) {
                        e(TAG, "Anchor hosted stored fail")
                        Snackbar.make(findViewById(android.R.id.content), "Anchor hosted stored fail", Snackbar.LENGTH_SHORT).show()
                    } else {
                        d(TAG, "Anchor hosted stored CloudId:  ${cloudAnchor!!.cloudAnchorId}, roomId: $roomId")
                        mGameController.storeUserInfo(mIsUserA, mFirebaseUser!!.uid, mFirebaseUser!!.displayName, mFirebaseUser!!.photoUrl!!.path)
                        Snackbar.make(findViewById(android.R.id.content), "Anchor hosted stored," +
                                " Room: $roomId", Snackbar.LENGTH_SHORT).show()

                        room = roomId
                        tv = findViewById(R.id.actionbar_room_number)
                        tv!!.text = roomId

                        mGameController.getUserInfo(false, this::onReadUserInfo)

                        mGameController.getAnimalInfoMove(false, this::onAnimalMoveUpdate)
                        mGameController.getAnimalInfoEat(false, this::onAnimalEatUpdate)
                        mGameController.setOnUserTurnListener(this::onUserTurn)
                        mGameController.setOnGameFinishListener(this::onGameFinish)
                        //mGameController.setOnTimeStartListenerB(this::onTimeStartB)
                        mGameController.setOnTimeEndListenerA(this::onTimeEndA)
                    }
                }
            } else {
                startCheckUpdatedAnchor()
                d(TAG, "Host Anchor state: $cloudState, start another check around")
            }
        } else if (appAnchorState == AppAnchorState.RESOLVING) {
            if (cloudState.isError) {
                appAnchorState = AppAnchorState.NONE
                mHandler.removeCallbacksAndMessages(null)
                Snackbar.make(findViewById(android.R.id.content), "Anchor resolving error:  state: $cloudState", Snackbar.LENGTH_SHORT).show()
                e(TAG, "Anchor hosted error:  CloudId: $cloudState")
            } else if (cloudState == Anchor.CloudAnchorState.SUCCESS) {
                appAnchorState = AppAnchorState.RESOLVED
                Snackbar.make(findViewById(android.R.id.content), "Anchor resolved successfully!", Snackbar.LENGTH_SHORT).show()
                d(TAG, "Anchor resolved successfully!")
                mGameController.getUserInfo(true, this::onReadUserInfo)
                mHandler.removeCallbacksAndMessages(null)

                tv = findViewById(R.id.actionbar_room_number)
                tv!!.text = room

                mGameController.getAnimalInfoMove(true, this::onAnimalMoveUpdate)
                mGameController.getAnimalInfoEat(true, this::onAnimalEatUpdate)
                mGameController.setOnUserTurnListener(this::onUserTurn)
                mGameController.setOnGameFinishListener(this::onGameFinish)
                //mGameController.setOnTimeStartListenerA(this::onTimeStartA)
                mGameController.setOnTimeEndListenerB(this::onTimeEndB)

                placeBoard()
            } else {
                startCheckUpdatedAnchor()
                d(TAG, "Resolve Anchor state: $cloudState start another check around")
            }
        }

    }

    private inner class DownloadImageTask(internal var bmImage: ImageView) : AsyncTask<String, Void, Bitmap>() {

        override fun doInBackground(vararg urls: String): Bitmap? {
            val urldisplay = urls[0]
            var mIcon11: Bitmap? = null
            try {
                val `in` = java.net.URL(urldisplay).openStream()
                mIcon11 = BitmapFactory.decodeStream(`in`)
            } catch (e: Exception) {
                e.message?.let { Log.e("Error", it) }
                e.printStackTrace()
            }

            return mIcon11
        }

        override fun onPostExecute(result: Bitmap) {
            bmImage.setImageBitmap(result)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_logout) {
            //logout()
            return true
        }
        else if(id == R.id.action_inside){
            val intent = Intent(this, LineChartActivity::class.java)
            val args = Bundle()
            args.putParcelableArrayList("SCORE", scoreList);
            intent.putExtra("BUNDLE", args)
            startActivity(intent)
        }
        else if(id == R.id.action_jitter){
            val intent = Intent(this, JitterActivity::class.java)
            val args = Bundle()
            args.putParcelableArrayList("SCORE", scoreList);
            intent.putExtra("BUNDLE", args)
            startActivity(intent)
        }
        return super.onOptionsItemSelected(item)
    }

    /*data class Score(
            val index: Int,
            val time: Long,
    )*/

    /*private fun onTimeStart(start: Long){
        startTime = start
    }*/

    private fun onTimeStartA(start: Long){
        startTimeA = start
    }

    private fun onTimeStartB(start: Long){
        startTimeB = start
    }

    /*private fun onTimeEnd(end: Long){
        var time = end - startTime
        i++
        scoreList.add(Score(i.toString(), time))
    }*/

    private fun onTimeEndA(perf: PerformanceModel){
        //var time = end - startTimeA
        val s = perf.index
        val starting = moveStartA[s]
        val endTime = getTrueTime()
        val time = endTime - starting!!
        scoreList.add(Score(s, time / 2))
        //lagA[s] = time / 2               // time represents RTT, so effective lag is time/2
        /*if(s == 3){
            writeCSV()
        }*/
        //writeCSV()
    }

    private fun onTimeEndB(perf: PerformanceModel){
        //var time = end - startTimeB
        val s = perf.index
        val starting = moveStartB[s]
        val endTime = getTrueTime()
        val time = endTime - starting!!
        scoreList.add(Score(s, time / 2))
        //lagB[s] = time / 2                 // time represents RTT, so effective lag is time/2
        /*if(s == 3){
            writeCSV()
        }*/
        //writeCSV()
    }


    private fun writeCSV() {

        val file = File(Environment.getExternalStorageDirectory(), "csv/test.txt")
        val outputStream = FileOutputStream(file)
        outputStream.write("test".toByteArray())
        outputStream.close()

        /*val FILENAME = "test.csv"
        val entry = "1234"

        val directoryDownload = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val logDir = File(directoryDownload, "Happy App Logs") //Creates a new folder in DOWNLOAD directory

        logDir.mkdirs()
        val file = File(logDir, FILENAME)
        val out = FileOutputStream(file)
        out.write(entry.toByteArray()) //Write the obtained string to csv

        out.close()*/
        //toastIt("Entry saved")


        /*val output = FileWriter(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS))
        CsvListWriter(output, CsvPreference.STANDARD_PREFERENCE).use { listWriter ->
            for ((key, value) in lagA) {
                listWriter.write(key, value)
            }
            for ((key, value) in lagB) {
                listWriter.write(key, value)
            }
        }

        println(output)*/
    }

    //private var firebaseAuth: FirebaseAuth? = null
    /*private fun logout() {
        mFirebaseAuth!!.signOut()
        Auth.GoogleSignInApi.signOut(mGoogleApiClient)
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        mGoogleApiClient = GoogleApiClient.Builder(this)
            .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
            .build()
        val signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient)
        startActivityForResult(
            signInIntent,
            RC_SIGN_IN)
    }*/

}


