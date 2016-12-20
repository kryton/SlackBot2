package actors.systems

import actors.BotMessages.BotMessages
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import play.api.Configuration
import slack.models.Attachment

import scala.concurrent.ExecutionContext

/**
  * Created by iholsman on 12/20/2016.
  */
class SlackTeamManager (configuration: Configuration) extends Actor with ActorLogging {
  def receive: Receive = {
    case _ => log.info("Unknown Message SlackTeamManager")
  }
}


object SlackTeamManager {
  def props( configuration: Configuration)(implicit ec: ExecutionContext) : Props = Props(classOf[SlackTeamManager], configuration)
  sealed trait SlackAPIMessage extends BotMessages
  case class SendMessage(channelID: String, text: String) extends SlackAPIMessage
  case class SendAttachment(channelID: String, text:String, attachments: Seq[Attachment]) extends SlackAPIMessage
  case class RecvMessage(userID:String, channelID: String, ts:String, text: String) extends SlackAPIMessage
  case class AddListener(actor:ActorRef)extends SlackAPIMessage
  case class AddUserChannelListener(actor:ActorRef, userID:String, channel:String)extends SlackAPIMessage
  case class RemoveListener(actor:ActorRef)extends SlackAPIMessage
  case class JoinAndCreate(channelName:String)extends SlackAPIMessage
  case class InviteUser(channelName:String, userID:String) extends SlackAPIMessage
  case class NewBlockingApi( apiToken: String) extends SlackAPIMessage
  case class SwitchToNewAPI( clientId: String, clientSecret:String, code:String, redirectURI:Option[String])

  case object Channels extends SlackAPIMessage
  case object Users extends SlackAPIMessage

}
