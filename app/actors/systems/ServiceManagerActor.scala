package actors.systems

import javax.inject.Inject

import actors.BotMessages.{BotMessages, End, GetConfig, Start}
import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.util.Timeout
import play.api.Configuration
import utils.ServiceManagerAPI

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, _}
/**
  * Created by iholsman on 10/31/2016.
  */

class ServiceManagerActor  @Inject() (configuration: Configuration)(implicit ec: ExecutionContext) extends Actor with ActorLogging {
  import ServiceManagerActor._
  implicit val timeout: Timeout = 5.seconds
  val user: String = configuration.getString("serviceManager.config.user").getOrElse("none")
  val password: String = configuration.getString("serviceManager.config.password").getOrElse("none")
  val url: String = configuration.getString("serviceManager.config.url").getOrElse("none")
  val  duration: FiniteDuration = 5.seconds
  implicit val actorSystem: ActorSystem = this.context.system
  override def preStart(): Unit = {
    super.preStart()
    self ! Start
  }
  def receive: Receive = {
    case Start =>
      val apiClient = ServiceManagerAPI(user,password, url, duration)
      context.become(receiveStarted( apiClient))
    case GetConfig =>
      sender() ! Config(user,url)
    case e: ServiceManagerMessage =>
      log.error("Not started.. attempting to start now")
      self ! Start
      self.tell(e,sender )
    case OpenP1P2 =>
      self ! Start
      self.tell( OpenP1P2, sender)
    case _ =>
      log.error("Unknown Message (receive)")

  }
  def receiveStarted(apiClient:ServiceManagerAPI) :Receive = {
    case Start =>{}
  //    log.warning("Already Started")
    case GetConfig =>
      sender() ! Config(user,url)
    case End =>
      context.become(receive)
    case i:Incident =>
      val senderActor = sender()
      apiClient.getDetail(i.incidentID).map( senderActor ! _)
    case l:IncidentList =>
      val senderActor = sender()
      apiClient.getList(l.days,l.maxPri).map( senderActor ! _)
    case OpenP1P2 =>
      val senderActor = sender()
      apiClient.getList(99,1, true).map( senderActor !_)
    case _ =>
      log.error("Unknown message (receiveStarted)")

  }
}

object ServiceManagerActor  {
  def props( configuration: Configuration)(implicit ec: ExecutionContext) : Props = Props(classOf[ServiceManagerActor], configuration)
  sealed trait ServiceManagerMessage extends BotMessages

  //case object Start extends ServiceManagerMessage
  //case object End extends ServiceManagerMessage
  //case object GetConfig extends ServiceManagerMessage
  case object P1P2ThisWeek extends ServiceManagerMessage
  case object OpenP1P2 extends ServiceManagerMessage
  case object P1ThisWeek extends ServiceManagerMessage
  case class Incident( incidentID:String) extends ServiceManagerMessage
  case class IncidentList( days:Int, maxPri:Int) extends ServiceManagerMessage
  case class Config(user:String, url:String) extends ServiceManagerMessage
}
