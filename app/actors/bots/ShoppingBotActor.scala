package actors.bots

import javax.inject.{Inject, Named}

import actors.BotMessages.{BotMessages, Start}
import actors.bots.ShoppingBotActor.{GetCategories, GetProduct, GetProductsInCategory}
import actors.systems.DRActor.{ApplyBillingAddress, CategoryDetail, CategoryList, NewUserSessionReply}
import slack.rtm.SlackAPIActor.{AddUserChannelListener, SendMessage}
import actors.systems.{DRActor, SlackTeamManager}
import actors.systems.SlackTeamManager.{Payload, PayloadResponse}
import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import play.api.Configuration
import slack.models.{ActionField, Attachment}
import slack.rtm.SlackAPIActor
import utils._

import scala.concurrent.{ExecutionContext, Future}
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
      val bits = p.callback_id.split("-",2)

       bits.headOption match {
         case Some(callbackType) =>
            callbackType match {
              case "TEST" =>
                sender() ! testCallback(bits.last, payload = p)
              case "CAT" =>
                sender() ! categoryCallback(bits.last, payload = p)

              case p:String =>
                sender() ! PayloadResponse(worked=false,response_type = SlackTeamManager.responseTypeEphermal, replace_original = false, text=s"unknown callback $p" )
            }

         case None =>
           sender() ! PayloadResponse(worked = false,  response_type = SlackTeamManager.responseTypeEphermal, replace_original =false, text="Unknown formatting of callback id.")
       }
    case  GetCategories(channelID:String, catId:Option[Long]) => getCategories(channelID,catId)
    case  GetProductsInCategory(channelId:String, catId:Long) => getProductInCategory(channelId,catId)
    case  GetProduct(channelId:String, productId:Long) => getProduct(channelId,productId)
    case _ => log.error("Unknown message")
  }

  protected def testCallback( callback:String, payload:Payload) : PayloadResponse = {
    PayloadResponse(worked = true, response_type = SlackTeamManager.responseTypeEphermal, replace_original =false, text="Received the Test Callback (in bot1).")
  }
  protected def productsInCategoryCallback( callback:String, payload:Payload) : PayloadResponse = {
    try {
      val num = callback.toLong
      self ! GetProductsInCategory(payload.channel.id,num)
      PayloadResponse(worked = true, response_type = SlackTeamManager.responseTypeEphermal, replace_original =false, text=s"Received the productsInCategoryCallback(in $callback).")
    } catch {
      case e:NumberFormatException =>
        PayloadResponse(worked = false, response_type = SlackTeamManager.responseTypeEphermal, replace_original =false, text=s"invalid callback $callback.")
    }

  }
  protected def categoryCallback( callback:String, payload:Payload) : PayloadResponse = {
    try {
      val catId = callback.toLong
      payload.actions.headOption match {
        case Some(action) => action.value match {
          case "CATPROD" =>
            self ! GetProductsInCategory(payload.channel.id,catId)
          case _ => self ! GetCategories(payload.channel.id,Some(catId))
        }
        case None => self ! GetCategories(payload.channel.id,Some(catId))
      }

    } catch {
      case e:NumberFormatException =>
        self ! GetCategories(payload.channel.id,None)
    }


    PayloadResponse(worked = true, response_type = SlackTeamManager.responseTypeEphermal, replace_original =false, text=s"Received the Test Callback (in $callback).")
  }

  protected def getCategories(channelID:String, catId:Option[Long]): Future[Unit] = {
    log.info(s"GetCategories $channelID $catId")
    (drActor ? DRActor.GetCategories(catId)).mapTo[Either[CategoryDetail,CategoryList]].map { e: Either[CategoryDetail, CategoryList] =>
      if ( e.isLeft) {
        val catDetail = e.left.get.category
      //  slackAPIActor ! SlackAPIActor.SendMessage( channelID, catDetail.displayName)
        val actionField = Seq(ActionField("products","Products","button",Some("primary"), value=Some(s"CATPROD") ))
        val attachment = Attachment( title = Some(catDetail.displayName), text = catDetail.shortDescription,callback_id = Some(s"CAT-${catDetail.id}"), actions=actionField, image_url = catDetail.thumbnailImage)
        slackAPIActor ! SlackAPIActor.SendAttachment(channelID, text=catDetail.displayName,attachments = Seq(attachment))
      } else {
        val categoryList = e.right.get.categories

        val attachments:Seq[Attachment] = categoryList.map{ cl =>
          val id = cl.uri.split('/').last
          val catId = s"CAT-$id"
          val prodId = s"CATPROD-$id"

          val actionField = Seq(
            ActionField("select", "Detail", "button", Some("primary"), value=Some("CAT")),
            ActionField("products", "Products", "button", Some("default"), value=Some("CATPROD"))
          )

          Attachment( text = Some(cl.displayName),  callback_id = Some(catId),  actions=actionField)
        }
        slackAPIActor ! SlackAPIActor.SendAttachment(channelID, text="Categories",attachments = attachments)
      }
    }
  }

  protected def getProductInCategory(channelID:String, catId:Long): Future[Unit] = {
    log.info(s"getProductInCategory $channelID $catId")
    (drActor ? DRActor.GetProductsInCategory(catId)).mapTo[Option[ProductsInCategory]].map { e: Option[ProductsInCategory] =>
      e match {
        case Some( productsInCategory) =>
          productsInCategory.product match {
            case None => slackAPIActor ! SendMessage(channelID, s"I can't find any products in category $catId")
            case Some(products:Seq[ProductInCategoryItem]) =>

              val attachments:Seq[Attachment] = products.map { productDetail: ProductInCategoryItem =>
                Attachment(title = Some(productDetail.displayName), text = Some(productDetail.pricing.formattedListPrice), thumb_url = productDetail.thumbnailImage )
              }
              slackAPIActor ! SlackAPIActor.SendAttachment(channelID, text = "Products In Category", attachments = attachments)
          }
        case None => slackAPIActor ! SendMessage(channelID,s"I can't find any products in category $catId")
      }
    }
  }
  protected def getProduct(channelID:String, productId:Long): Future[Unit] = {
    log.info(s"getProduct $channelID $productId")
    (drActor ? DRActor.GetProduct(productId)).mapTo[Option[ProductDetail]].map { e: Option[ProductDetail] =>
      e match {
        case Some( productDetail ) =>
            val attachments = Seq(
                Attachment(title = Some(productDetail.displayName), text = productDetail.longDescription),
                Attachment( text = Some(productDetail.pricing.formattedListPrice), image_url = productDetail.productImage)
            )

              slackAPIActor ! SlackAPIActor.SendAttachment(channelID, text = productDetail.displayName, attachments = attachments)
        case None => slackAPIActor ! SendMessage(channelID,s"I can't find any products $productId")
      }
    }
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
        case msgText if msgText.toLowerCase.contains("categories") =>  getCategories(msg.channelID, None)

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
  case class GetCategories(channelId:String, catId:Option[Long]) extends ShoppingBotMessage
  case class GetProductsInCategory(channelId:String, catId:Long) extends ShoppingBotMessage
  case class GetProduct(channelId:String, productId:Long) extends ShoppingBotMessage
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
