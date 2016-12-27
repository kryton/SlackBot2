package actors.bots

import javax.inject.{Inject, Named}

import actors.BotMessages.{BotMessages, Start}
import actors.systems.DRActor.{ApplyBillingAddress, CategoryDetail, CategoryList, NewUserSessionReply}
import slack.rtm.SlackAPIActor.AddUserChannelListener
import actors.systems.DRActor
import actors.systems.SlackTeamManager.{Payload, PayloadResponse}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import play.api.Configuration
import slack.models.{ActionField, Attachment}
import slack.rtm.SlackAPIActor
import utils.{Cart, DRAPI}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
/**
  * Created by iholsman on 11/7/2016.
  */
class ShoppingBotActor( slackAPIActor: ActorRef, drActor: ActorRef ) extends Actor with ActorLogging {

  implicit val timeout: Timeout = 15.seconds


  override def receive: Receive = {

    case Start =>
      slackAPIActor ! SlackAPIActor.AddListener(self)
      drActor ! Start
    case r: SlackAPIActor.RecvMessage => parseMessage(r)
    case p: Payload =>
       p.callback_id.split('-').headOption match {
         case Some(callbackType) =>
           sender() ! PayloadResponse(worked = true,"{  \"response_type\": \"ephemeral\",  \"replace_original\": false,  \"text\": \"Received the Test Callback (in bot).\"}")
         case None =>
           sender() ! PayloadResponse(worked = false,"{  \"response_type\": \"ephemeral\",  \"replace_original\": false,  \"text\": \"Unknown formatting of callback id.\"}")
       }

    case _ => log.error("Unknown message")
  }

  protected def parseMessage(msg: SlackAPIActor.RecvMessage): Unit = {
    case class Resp(ps: Seq[String], q: Long, p: Option[String])

    if (msg.text.startsWith("!")) {
      msg.text match {
        case msgText if msgText.toLowerCase.contains("session") =>
          (drActor ? DRActor.NewUserSession("Slack", msg.userID)).mapTo[NewUserSessionReply].map { s: NewUserSessionReply =>
            if (s.worked) {
              val shoppingBotActorUser = context.actorOf(Props(new ShoppingBotActorUser(slackAPIActor, s.sessionActor, msg.channelID, msg.userID)), s"ShoppingBotUserActor_${msg.channelID}_${msg.userID}")
              shoppingBotActorUser ! Start
              context.watch(shoppingBotActorUser)
              log.info(s"Session returned - ${s.worked}")

            } else {
              slackAPIActor ! SlackAPIActor.SendMessage(msg.channelID, "I couldn't get a session token")
            }
          }
        case msgText if msgText.contains("test") =>
          val actionField = Seq(ActionField("accept", "Accept", "button", Some("primary")))
          val attachment = Attachment(text = Some("Do you want to accept?"),
            fallback = Some("backup message: code-123456"),
            callback_id = Some("TEST-TEST"), actions = actionField)
          slackAPIActor ! SlackAPIActor.SendAttachment(msg.channelID, text="Test",attachments = Seq(attachment))
        case msgText if msgText.toLowerCase.contains("categories") =>

          (drActor ? DRActor.GetCategories(None)).mapTo[Either[CategoryDetail,CategoryList]].map { e: Either[CategoryDetail, CategoryList] =>
            log.info("GetCategories - Got Response!")
           if ( e.isLeft) {
             slackAPIActor ! SlackAPIActor.SendMessage( msg.channelID, e.left.get.category.displayName)
           } else {
             val categoryList = e.right.get.categories
             val actionField = Seq(ActionField("select", "Select", "button", Some("primary")))
             val attachments:Seq[Attachment] = categoryList.map{ cl =>
               val catId = "CAT-" + cl.uri.split('/').last
               Attachment( text = Some(cl.displayName), callback_id = Some(catId), actions=actionField)
             }
             slackAPIActor ! SlackAPIActor.SendAttachment(msg.channelID, text="Categories",attachments = attachments)
           }

          }
        case msgText if msgText.toLowerCase.contains("cart") => {}
        case msgText if msgText.toLowerCase.contains("addzip") => {}
        case msgText if msgText.toLowerCase.contains("additem") => {}
        case msgText if msgText.toLowerCase.contains("checkout") => {}
        case _ => slackAPIActor ! SlackAPIActor.SendMessage(msg.channelID,s"I don't understand ${msg.text}")
      }
    }
  }

}


object ShoppingBotActor {
  def props( slackAPIActor: ActorRef,  drActor: ActorRef          ) : Props =    Props(classOf[ShoppingBotActor], slackAPIActor, drActor )

  sealed trait ShoppingBotMessage extends BotMessages

  case class Config(friendlyName: String) extends ShoppingBotMessage

}

class ShoppingBotActorUser (slackAPIActor: ActorRef,
                            drActorUser: ActorRef,
                            channel:String, user:String)(implicit ec: ExecutionContext)  extends Actor with ActorLogging {
  implicit val timeout: Timeout = 5.seconds

  override def receive = {
    case Start =>
      slackAPIActor ! SlackAPIActor.SendMessage(channel,"You now have a session")
      slackAPIActor ! AddUserChannelListener(self,user,channel)
    case r: SlackAPIActor.RecvMessage =>
      //log.warning(s"In ActorUser ${r.text}")
      parseMessage(r)
  }

  protected def parseMessage(msg: SlackAPIActor.RecvMessage): Unit = {
    case class Resp(ps: Seq[String], q: Long, p: Option[String])

    if (msg.text.startsWith("!")) {
        if (msg.text.contains("cart")) {
          (drActorUser ?  DRActor.GetCart).mapTo[Option[Cart]].map {
            case Some(cart: Cart) =>
              log.info("Cart returned")
              slackAPIActor ! SlackAPIActor.SendMessage(msg.channelID, s"Cart  ${cart.id} - Items ${cart.totalItemsInCart} - ${cart.businessEntityCode} - Total ${cart.pricing.formattedOrderTotal}")
            case None =>
              log.info("No Cart returned")
              slackAPIActor ! SlackAPIActor.SendMessage(msg.channelID, "Something went wrong.")
          }
        } else {
          if (msg.text.contains("addzip")) {
            val zipReg = "^.*\\s([0-9]+)\\s+(..).*$".r
            msg.text match {
              case zipReg(zip, country) =>
                (drActorUser ?  ApplyBillingAddress(zip, country)).mapTo[Option[Cart]].map {
                  case Some(cart: Cart) =>
                    log.info("Cart returned")
                    slackAPIActor ! SlackAPIActor.SendMessage(msg.channelID, s"Cart ${cart.id} - Items ${cart.totalItemsInCart} - ${cart.businessEntityCode} - Total ${cart.pricing.formattedOrderTotal}")
                  case None =>
                    log.info("No Cart returned")
                    slackAPIActor ! SlackAPIActor.SendMessage(msg.channelID, "Something went wrong.")
                }
              case _ => slackAPIActor ! SlackAPIActor.SendMessage(msg.channelID, "Usage\n! addzip 55555 US")
            }
          } else {
            if (msg.text.contains("additem")) {
              val p1 = "^.*\\s([0-9,]+)".r
              val p2 = "^.*\\s([0-9,]+)\\s+([0-9]+)".r
              val p3 = "^.*\\s([0-9,]+)\\s+(.+)".r
              val p4 = "^.*\\s([0-9,]+)\\s+([0-9]+)\\s(.+)".r

              val rr: Option[Resp] = msg.text match {
                case p4(products, qty, promo) => Some(Resp(products.split(",").toSeq, qty.toLong, Some(promo)))
                case p2(products, qty) => Some(Resp(products.split(",").toSeq, qty.toLong, None))
                case p3(products, promo) => Some(Resp(products.split(",").toSeq, 1, Some(promo)))
                case p1(products) => Some(Resp(products.split(",").toSeq, 1, None))
                case _ =>
                  None
              }

              if (rr.isDefined) {
                log.info("About to seend Add product")
                (drActorUser ? DRActor.AddProduct(rr.get.ps, rr.get.p, rr.get.q)).mapTo[Option[Cart]].map {
                  case Some(cart: Cart) =>
                    log.info("Add Product returned")
                    slackAPIActor ! SlackAPIActor.SendMessage(msg.channelID, s"Cart ${cart.id} - Items ${cart.totalItemsInCart} - ${cart.businessEntityCode} - Total ${cart.pricing.formattedOrderTotal}")
                  case None =>
                    log.info("Add Product Failed")
                    slackAPIActor ! SlackAPIActor.SendMessage(msg.channelID, "Something went wrong.")
                }
              } else {
                log.error("Invalid response.. ! addItem sku [quantity] [promocode] ")
                slackAPIActor ! SlackAPIActor.SendMessage(msg.channelID, "Invalid response.. ! addItem sku [quantity] [promocode] ")
              }
            } else {
              if ( msg.text.contains("checkout")) {
                (drActorUser ?  DRActor.GetCheckoutURL).mapTo[Option[String]].map {
                  case Some(cart: String) =>
                    log.info("Checkout returned")
                    slackAPIActor ! SlackAPIActor.SendMessage(msg.channelID, s"Please Click $cart")
                  case None =>
                    log.info("No checkout returned")
                    slackAPIActor ! SlackAPIActor.SendMessage(msg.channelID, "Something went wrong.")
                }
              } else {

                // slackAPIActor ! SlackAPIActor.SendMessage(msg.channel, "I don't understand that")
              }
            }
          }
      }

    }
  }
}
object ShoppingBotActorUser {
  def props(slackAPIActor:ActorRef, drActorUser:ActorRef, source:String, userKey:String)(implicit ec: ExecutionContext) : Props = Props(classOf[ShoppingBotActorUser], slackAPIActor, drActorUser, source,userKey)
}
