package actors.systems

import javax.inject.Inject

import actors.BotMessages.{BotMessages, Start}
import actors.systems.DRActor._
import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Terminated}
import akka.util.Timeout
import play.api.Configuration
import utils._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{FiniteDuration, _}
import scala.reflect.internal.util.Statistics.Quantity

/**
  * Created by iholsman on 11/9/2016.
  */
class DRActor  @Inject() (configuration: Configuration)(implicit ec: ExecutionContext) extends Actor with ActorLogging {
  implicit val timeout: Timeout = 5.seconds
  val apiKey: String = configuration.getString("DR.config.key").getOrElse("missing configuration DR.config.key")
  val url: String = configuration.getString("DR.config.url").getOrElse("missing configuration DR.config.url")
  val duration: FiniteDuration = 5.seconds
  implicit val system = ActorSystem()
  override def preStart(): Unit = {
    super.preStart()
    self ! Start
  }
  override def receive: Receive = {
    case Start =>
      val drAPI = DRAPI(apiKey, url)

      context.become(receive2(drAPI, Map.empty, Map.empty))
    case nu: NewUserSession =>
      val drAPI = DRAPI(apiKey, url)
      val newActor: ActorRef = context.actorOf(Props(new DRActorUser(drAPI, nu.source, nu.userKey)), s"DRActor_${nu.source}_${nu.userKey}")
      context.watch(newActor)
      newActor.forward( Start)
      context.become(receive2(drAPI,Map.empty + (nu -> newActor), Map.empty + (newActor -> nu)))


  }
  def receive2( drAPI:DRAPI, sessionsActor:Map[NewUserSession, ActorRef], actorSession:Map[ActorRef,NewUserSession]): Receive = {
    case nu: NewUserSession =>
      sessionsActor.get(nu) match {
        case None =>
          val newActor: ActorRef = context.actorOf(Props(new DRActorUser(drAPI, nu.source, nu.userKey)), s"DRActor_${nu.source}_${nu.userKey}")
          context.watch(newActor)
          newActor.forward(Start)
          context.become(receive2(drAPI, sessionsActor + (nu -> newActor), actorSession + (newActor -> nu)))
        case Some(actor) => sender ! "You already have a session"
      }
    case  GetCategories(id:Option[Long]) =>
      val sendBack = sender
      drAPI.getCategories(id).map {  x:Either[Category,Seq[CategoryMinimum]] =>
        val resp:Either[CategoryDetail,CategoryList] =
        if ( x.isLeft ) {
         Left(  DRActor.CategoryDetail(x.left.get))
        } else {
          log.warning("Sending Cat List back")
          Right( DRActor.CategoryList( x.right.get))
        }
        sendBack ! resp
      }
    case  GetProductsInCategory(id:Long) =>
      val sendBack = sender
      drAPI.getProductsInCategory(id).map {  resp:Option[ProductsInCategory] =>
        sendBack ! resp
      }
   case GetProduct(id:Long) =>
      val sendBack = sender
      drAPI.getProductDetail(id).map {  resp:Option[ProductDetail] =>
        sendBack ! resp
      }

    case GetDRUserActor(source, user) => sender ! sessionsActor.get(NewUserSession(source,user))

    case DRForward(source,user,message) =>
      sessionsActor.get(NewUserSession(source,user)) match {
        case Some(a: ActorRef) => a.forward(message)
        case None => log.warning(s"Can't find info for $source $user")
          sender ! None
      }

    case Terminated(actor: ActorRef) =>
      actorSession.get(actor) match {
        case Some(n: NewUserSession) =>
          context.become(receive2(drAPI, sessionsActor - n, actorSession - actor))
        case None =>
          log.error("System isn't in sync - Terminated Actor not found in newUserSession list")
      }
      context.become(receive2(drAPI, Map.empty, Map.empty))
  }
}


class DRActorUser (drAPI:DRAPI, source:String, userKey:String)(implicit ec: ExecutionContext)  extends Actor with ActorLogging {

  override def receive: Receive = {
    case Start =>
      val sendBack = sender()
      drAPI.getToken.map { x: Option[Token] =>
        x match {
          case Some(t: Token) =>
            log.info(s"We got token $t")
            sendBack ! NewUserSessionReply(self,worked = true)
            //sendBack ! s"OK ${t.token_type}"
            context.become(receiveToken(t))

          case None =>
            //sendBack ! "Something failed"
            sendBack ! NewUserSessionReply(self,worked = false)
            log.error(s"DRAPI-getToken failed for $source/$userKey" )
        }
      }

    case _ =>
      log.error("Unknown message received")
  }
  def receiveToken(token:Token): Receive = {
    /* we might possibly get a 2nd request if the first one failed for some reason after we had it ?*/
    case Start =>
      val sendBack = sender()
      drAPI.getToken.map { x: Option[Token] =>
        x match {
          case Some(t: Token) =>
            log.info(s"We got token $t")
            sendBack ! NewUserSessionReply(self,worked = true)
            context.become(receiveToken(t))

          case None =>
            //sendBack ! "Something failed"
            sendBack ! NewUserSessionReply(self,worked = false)
            log.error(s"DRAPI-getToken failed for $source/$userKey" )
        }
      }

    case GetCart =>
          val sendBack:ActorRef = sender()
          drAPI.getCart(token.access_token).map{ oc:Option[Cart] =>
            log.info("Cart Returned")
            sendBack ! oc
      }

    case GetCheckoutURL =>
          val sendBack:ActorRef = sender()
          drAPI.webCheckout(token.access_token).map{ oc:Option[String] =>
            log.info(s"Webcheckout Returned $oc")
            sendBack ! oc
      }

    case ApplyBillingAddress(postalCode,country) =>
      val sendBack:ActorRef = sender()
      drAPI.applyBillingAddress(token.access_token,postalCode,country).map{ oc:Option[Cart] =>
        sendBack ! oc
    }
    case AddProduct(productIds, promoCode, quantity) =>
      val sendBack:ActorRef = sender()
      drAPI.addItem(token.access_token,productIds,promoCode,quantity).map { oc: Option[Cart] =>
        log.info(s"Add Item Cart = ${oc.isDefined}")
        sendBack ! oc
      }

    case _ =>
      log.error("Unknown message received")
  }
}
object DRActorUser {
  def props(drAPI:DRAPI, source:String, userKey:String)(implicit ec: ExecutionContext) : Props = Props(classOf[DRActorUser], drAPI, source,userKey)
}


object DRActor  {
  sealed trait DRMessage extends BotMessages

  /**
    * create a new user session in DR (ie. grab a token)
    * @param source source of the request, typically the actorName where it is coming in from (eg.. FB, Slack, MSFT-Team)
    * @param userKey a unique identifier of that system
    */
  case class NewUserSession(source:String, userKey:String) extends DRMessage
  case class NewUserSessionReply(sessionActor:ActorRef, worked:Boolean) extends DRMessage
  case class DRForward(source:String, userKey:String, drMessage:DRMessage) extends DRMessage

  case class GetDRUserActor(source:String, userKey:String) extends DRMessage
  case class GetProductsInCategory(id:Long) extends DRMessage
  case class GetProduct(id:Long) extends DRMessage
  case class GetCategories(id:Option[Long]) extends DRMessage
  case class CategoryDetail(category:Category) extends DRMessage
  case class CategoryList(categories:Seq[CategoryMinimum]) extends DRMessage

  case object GetCart extends DRMessage
  case object GetCheckoutURL extends DRMessage
  case class ApplyBillingAddress( postalCode:String, country:String) extends DRMessage
  case class AddProduct( productIds:Seq[String], promoCode:Option[String], quantity: Long) extends DRMessage

  def props( configuration: Configuration)(implicit ec: ExecutionContext) : Props = Props(classOf[DRActor], configuration)
}
