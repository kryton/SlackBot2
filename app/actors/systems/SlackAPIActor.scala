package actors.systems

import javax.inject._

import actors.BotMessages.{BotMessages, End, GetConfig, Start}
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import play.api.Configuration
import slack.api.BlockingSlackApiClient
import slack.models.{Attachment, SlackEvent}
import slack.rtm.SlackRtmConnectionActor.AddEventListener
import slack.rtm.{RtmState, SlackRtmConnectionActor}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
  * Created by iholsman on 10/28/2016.
  */
class SlackAPIActor @Inject()(configuration: Configuration)(implicit ec: ExecutionContext) extends Actor with ActorLogging {

  import SlackAPIActor._

  implicit val timeout: Timeout = 5.seconds
  val apiKey: String = configuration.getString("slack.config.key").getOrElse("none")
  val duration: FiniteDuration = 5.seconds
  override def preStart(): Unit = {
    super.preStart()
    self ! Start
  }
  def receive: Receive = {
    case Start =>
      val apiClient = BlockingSlackApiClient(apiKey, duration)

      val state = RtmState(apiClient.startRealTimeMessageSession())
      val slackActor = SlackRtmConnectionActor(apiKey, state, duration)
      slackActor ! AddEventListener(self)
      context.become(receiveStarted(slackActor, apiClient,Set.empty,Map.empty,Map.empty))
    case a:AddListener =>
      val apiClient = BlockingSlackApiClient(apiKey, duration)
      val state = RtmState(apiClient.startRealTimeMessageSession())
      val slackActor = SlackRtmConnectionActor(apiKey, state, duration)
      slackActor ! AddEventListener(self)
      context.watch(a.actor)
      context.become( receiveStarted(slackActor,apiClient, Set(a.actor),Map.empty,Map.empty))


    case Channels =>
      val apiClient = BlockingSlackApiClient(apiKey, duration)
      sender() ! apiClient.listChannels(1)
    case Users =>
      val apiClient = BlockingSlackApiClient(apiKey, duration)
      sender() ! apiClient.listUsers()
    case _ =>
      log.error("Can't do that in this state")
  }

  def receiveStarted(slackActor: ActorRef, apiClient: BlockingSlackApiClient, listeners:Set[ActorRef],
                     channelUserListen:Map[(String,String),ActorRef],
                     reverseChanelUserListen:Map[ActorRef,(String,String)]): Receive = {
    case Start =>
      log.warning("Already Started")
    case m: slack.models.Message =>
      listeners.foreach( _ ! RecvMessage(m.user, m.channel,m.ts, m.text))
      val oActor:Option[ActorRef] = channelUserListen.get((m.channel,m.user))
      oActor match {
        case Some(actorRef) => actorRef ! RecvMessage(m.user, m.channel,m.ts, m.text)
        case _ =>
      }

    case hello: slack.models.Hello =>
      log.debug(s"Hello MSG")
    case ut: slack.models.UserTyping =>
      log.debug(s"User Typing ${ut.channel} ${ut.user}")
    case s: slack.models.ReconnectUrl =>
      log.debug(s"Reconnect ${s.url}")
    case s: slack.models.PresenceChange =>
      log.debug(s"Presence ${s.user}/${s.presence}")
    case reply: slack.models.Reply =>
      log.info(s"Reply ${reply.reply_to} - ${reply.text} ${reply.ok}")
    case s: SlackEvent =>
      log.warning(s"Slack Event ${s.getClass}")
    case m: SendMessage =>
      slackActor.ask(SlackRtmConnectionActor.SendMessage(m.channel, m.text))(sender = sender(), timeout = timeout)
    case a: SendAttachment =>
      apiClient.postChatMessage( channelId = a.channel, text= a.text, attachments = Some(a.attachments))
    case Channels =>
      sender() ! apiClient.listChannels(1)
    case Users =>
      sender() ! apiClient.listUsers()
    case GetConfig =>
      sender() ! apiKey
    case id: Long =>
      log.info(s"Recieved ID $id")
    case End =>
      slackActor ! Terminated
      context.become(receive)
    case a:AddListener =>
      context.watch(a.actor)
      context.become( receiveStarted(slackActor,apiClient, listeners + a.actor, channelUserListen,reverseChanelUserListen))
    case a:AddUserChannelListener =>
      context.watch( a.actor)
      context.become( receiveStarted(slackActor, apiClient, listeners,channelUserListen + ((a.channel,a.user)-> a.actor), reverseChanelUserListen + (a.actor->(a.channel,a.user))))
    case Terminated(actor) =>
      val oCU:Option[(String,String)] = reverseChanelUserListen.get(actor)
      oCU match {
        case None =>context.become( receiveStarted(slackActor,apiClient, listeners - actor, channelUserListen,reverseChanelUserListen))
        case Some(x)=> context.become( receiveStarted(slackActor,apiClient, listeners - actor,channelUserListen - x,reverseChanelUserListen - actor))
      }

    case a:RemoveListener =>
      val oCU:Option[(String,String)] = reverseChanelUserListen.get(a.actor)
      oCU match {
        case None =>context.become( receiveStarted(slackActor,apiClient, listeners - a.actor, channelUserListen,reverseChanelUserListen))
        case Some(x)=> context.become( receiveStarted(slackActor,apiClient, listeners - a.actor,channelUserListen - x,reverseChanelUserListen - a.actor))
      }
    case _ =>
      log.error("Unknown message")
  }


}


object SlackAPIActor {
  def props( configuration: Configuration)(implicit ec: ExecutionContext) : Props = Props(classOf[SlackAPIActor], configuration)
  sealed trait SlackAPIMessage extends BotMessages
  case class SendMessage(channel: String, text: String) extends SlackAPIMessage
  case class SendAttachment(channel: String, text:String, attachments: Seq[Attachment]) extends SlackAPIMessage
  case class RecvMessage(user:String, channel: String, ts:String, text: String) extends SlackAPIMessage
  case class AddListener(actor:ActorRef)extends SlackAPIMessage
  case class AddUserChannelListener(actor:ActorRef, user:String, channel:String)extends SlackAPIMessage
  case class RemoveListener(actor:ActorRef)extends SlackAPIMessage

  case object Channels extends SlackAPIMessage
  case object Users extends SlackAPIMessage

}
