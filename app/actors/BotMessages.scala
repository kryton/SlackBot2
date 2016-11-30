package actors

/**
  * Created by iholsman on 11/9/2016.
  */
object BotMessages {
  trait BotMessages

  case object Start extends BotMessages
  case object End extends BotMessages
  case object GetConfig extends BotMessages
}
