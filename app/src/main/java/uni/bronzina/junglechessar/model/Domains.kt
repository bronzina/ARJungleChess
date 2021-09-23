package uni.bronzina.junglechessar.model

data class PerformanceModel(var index: Int = 0, var time: Long = 0)

data class Tile(var posCol: Int = 0, var posRow: Int = 0,
                var tileType: TileType = TileType.TILE_GRASS)

data class Animal(var posCol: Int = 0, var posRow: Int = 0,
                  var state: AnimalState = AnimalState.ALIVE,
                  var animalType: AnimalType = AnimalType.RAT,
                  var animalDrawType: AnimalDrawType = AnimalDrawType.TYPE_A,
                  var inTrap: Boolean = false)

data class ChessUserInfo(var uid: String = "", var displayName: String = "",
                         var photoUrl: String = "", var userType: UserType = UserType.USER_A)

enum class UserType {
    USER_A,
    USER_B
}

enum class GameState {
    USER_A_TURN,
    USER_B_TURN,
    USER_A_WIN,
    USER_B_WIN,
    NO_WIN_USER
}

enum class AnimalType (val index: Int){
    RAT (1),
    CAT (2),
    DOG (3),
    WOLF (4),
    LEOPARD (5),
    TIGER (6),
    LION (7),
    ELEPHANT (8)
}

enum class AnimalTypeA (var index: Int){
    RAT (1),
    CAT (2),
    DOG (3),
    WOLF (4),
    LEOPARD (5),
    TIGER (6),
    LION (7),
    ELEPHANT (8)
}

enum class AnimalTypeB (var index: Int){
    RAT (1),
    CAT (2),
    DOG (3),
    WOLF (4),
    LEOPARD (5),
    TIGER (6),
    LION (7),
    ELEPHANT (8)
}

enum class AnimalDrawType {
    TYPE_A,
    TYPE_B
}

enum class AnimalState {
    ALIVE,
    DEAD
}

enum class EndPointType {
    DEVELOP,
    RELEASE
}

enum class TileType() {
    TILE_GRASS,
    TILE_TRAP,
    TILE_RIVER,
    TILE_BASEMENT
}