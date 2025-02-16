package lila.setup

import chess.format.Fen
import chess.variant.{ FromPosition, Variant }
import chess.{ ByColor, Clock }

import lila.common.Days
import lila.game.{ Game, IdGenerator, Player, Pov, Source }
import lila.lobby.Color
import lila.rating.PerfType
import lila.user.GameUser

final case class ApiAiConfig(
    variant: Variant,
    clock: Option[Clock.Config],
    daysO: Option[Days],
    color: Color,
    level: Int,
    fen: Option[Fen.Epd] = None
) extends Config
    with Positional:

  val strictFen = false

  val days      = daysO | Days(2)
  val time      = clock.so(_.limit.roundSeconds / 60)
  val increment = clock.fold(Clock.IncrementSeconds(0))(_.incrementSeconds)
  val timeMode =
    if clock.isDefined then TimeMode.RealTime
    else if daysO.isDefined then TimeMode.Correspondence
    else TimeMode.Unlimited

  private def game(user: GameUser)(using IdGenerator): Fu[Game] =
    fenGame: chessGame =>
      PerfType(chessGame.situation.board.variant, chess.Speed(chessGame.clock.map(_.config)))
      Game
        .make(
          chess = chessGame,
          players = ByColor: c =>
            if creatorColor == c
            then Player.make(c, user)
            else Player.makeAnon(c, level.some),
          mode = chess.Mode.Casual,
          source = if chessGame.board.variant.fromPosition then Source.Position else Source.Ai,
          daysPerTurn = makeDaysPerTurn,
          pgnImport = None
        )
        .withUniqueId
    .dmap(_.start)

  def pov(user: GameUser)(using IdGenerator) = game(user).dmap { Pov(_, creatorColor) }

  def autoVariant =
    if variant.standard && fen.exists(!_.isInitial)
    then copy(variant = FromPosition)
    else this

object ApiAiConfig extends BaseConfig:

  // lazy val clockLimitSeconds: Set[Int] = Set(0, 15, 30, 45, 60, 90) ++ (2 to 180).view.map(60 *).toSet

  def from(
      l: Int,
      v: Option[Variant.LilaKey],
      cl: Option[Clock.Config],
      d: Option[Days],
      c: Option[String],
      pos: Option[Fen.Epd]
  ) =
    ApiAiConfig(
      variant = Variant.orDefault(v),
      clock = cl,
      daysO = d,
      color = Color.orDefault(~c),
      level = l,
      fen = pos
    ).autoVariant
