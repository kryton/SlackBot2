package utils

import java.text.SimpleDateFormat
import java.util.{Calendar, Date}

import akka.actor.ActorSystem
import akka.actor.Status.Success
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.model.{Uri, _}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.ask
import akka.stream.ActorMaterializer
import play.api.Logger
//import play.api.libs.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json
import spray.json.{JsNumber, JsString, JsValue}
import spray.json.{DefaultJsonProtocol, JsonFormat}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

/**
  * Created by iholsman on 10/31/2016.
  */
object DRAPI {

  def apply(apiKey: String,
            url: String,
            duration: FiniteDuration = 5.seconds)(implicit actorSystem: ActorSystem, executionContext: ExecutionContext): DRAPI = {
    new DRAPI(apiKey, url, duration)
  }

}

case class Token(access_token: String, token_type: String, expires_in: Long, refresh_token: String)

case class CartLineItems(uri: String, lineItem: Option[Seq[CartLineItem]])

case class CartLineItem(uri: String, id: Long, quantity: Long, product: CartLineProduct, pricing: CartLinePricing)

case class CartLineProduct(uri: String, displayName: String, thumbnailImage: String)

case class CartLinePricing(listPrice: Amount,
                           listPriceWithQuantity: Amount,
                           salePriceWithQuantity: Amount,
                           formattedListPrice: String,
                           formattedListPriceWithQuantity: String,
                           formattedSalePriceWithQuantity: String)

case class Address(uri: Option[String],
                   firstName: Option[String],
                   lastName: Option[String],
                   line1: Option[String],
                   line2: Option[String],
                   city: Option[String],
                   countrySubdivision: Option[String],
                   postalCode: Option[String],
                   country: Option[String],
                   phoneNumber: Option[String])

case class Payment(uri: Option[String])

case class ShippingMethod(uri: Option[String])

case class ShippingOptions(uri: String)

case class Amount(currency: String, value: Double)

case class CartPricing(subtotal: Amount,
                       discount: Amount,
                       shippingAndHandling: Amount,
                       tax: Amount,
                       orderTotal: Amount,
                       formattedSubtotal: String,
                       formattedDiscount: String,
                       formattedShippingAndHandling: String,
                       formattedTax: String,
                       formattedOrderTotal: String
                      )

case class Cart(id: Either[Long, String],
                lineItems: CartLineItems,
                totalItemsInCart: Int,
                businessEntityCode: Option[String],
                billingAddress: Address,
                shippingAddress: Address,
                payment: Payment,
                shippingMethod: ShippingMethod,
                shippingOptions: ShippingOptions,
                pricing: CartPricing)

case class CartResponse(cart: Cart)

case class Category( uri:String, id:Long, locale:String, name:String, displayName:String, shortDescription:Option[String], longDescription:Option[String], thumbnailImage:Option[String])
case class CategoryMinimum( uri:String, displayName:String)
case class CategoryMinimumResp( uri:String,category: Seq[CategoryMinimum])
case class CategoryResponse( categories:Option[CategoryMinimumResp], category:Option[Category])

trait JsonDRMarshallers extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val tokenFmt = jsonFormat4(Token)
  implicit val amountFmt = jsonFormat2(Amount)

  implicit val cartLineProductFmt = jsonFormat3(CartLineProduct)
  implicit val cartLinePricingFmt = jsonFormat6(CartLinePricing)

  implicit val cartLineItemFmt = jsonFormat5(CartLineItem)
  implicit val cartLineItemsFmt = jsonFormat2(CartLineItems)

  implicit val addressFmt = jsonFormat10(Address)
  implicit val paymentFmt = jsonFormat1(Payment)
  implicit val shippingMethodFmt = jsonFormat1(ShippingMethod)
  implicit val shippingOptionsFmt = jsonFormat1(ShippingOptions)

  implicit val cartPricingFmt = jsonFormat10(CartPricing)
  implicit val cartFmt = jsonFormat10(Cart)
  implicit val cartResponseFmt = jsonFormat1(CartResponse)

  implicit val categoryFmt = jsonFormat8(Category)
  implicit val categoryMinimumFmt = jsonFormat2(CategoryMinimum)
  implicit val categoryMinimumRespFmt = jsonFormat2(CategoryMinimumResp)
  implicit val categoryResponseFmt = jsonFormat2(CategoryResponse)

}

object JsonDRMarshallers extends JsonDRMarshallers

class DRAPI(protected val apiKey: String,
            url: String,
            duration: FiniteDuration = 5.seconds)(implicit actorSystem: ActorSystem,
                                                  executionContext: ExecutionContext) {

  import utils.JsonDRMarshallers._

  private val log = Logger.logger
  implicit val materializer = ActorMaterializer()


  def getToken: Future[Option[Token]] = {
    val uri = Uri(s"$url/shoppers/token")
    val qry: Uri.Query = Uri.Query("apiKey" -> apiKey, "format" -> "json")

    val request = HttpRequest(HttpMethods.GET, uri = uri.withQuery(qry))

    Http().singleRequest(request).map { response =>
      response.status match {
        case StatusCodes.OK =>
          import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

          val result: Future[Token] = Unmarshal[HttpEntity](response.entity).to[Token]

          val detail = result.map { x => Some(x) }
          result.onFailure {
            case y =>
              log.warn(response.entity.toString)
              log.error(y.getMessage)
          }
          detail

        case _ =>
          log.error(s"URI= ${uri.toString()}")
          log.error(s"Invalid status code of ${response.status} -${response.entity}")
          None
          Future(None)
      }
    }.flatMap(identity)
  }

  def getCart(token: String): Future[Option[Cart]] = {
    val uri = Uri(s"$url/shoppers/me/carts/active")
    //https://dispatch-test.digitalriver.com/v1/shoppers/me/carts/active.drivejson?token=c96"
    val qry: Uri.Query = Uri.Query("token" -> token, "format" -> "json")

    val request = HttpRequest(HttpMethods.GET, uri = uri.withQuery(qry))

    Http().singleRequest(request).map { response =>
      response.status match {
        case StatusCodes.OK =>
          import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

          val result: Future[CartResponse] = Unmarshal[HttpEntity](response.entity).to[CartResponse]

          val detail = result.map { x => Some(x.cart) }
          result.onFailure {
            case y =>
              log.warn(response.entity.toString)
              log.error(y.getMessage)
          }
          detail

        case _ =>
          log.error(s"URI= ${uri.toString()}")
          log.error(s"Invalid status code of ${response.status} -${response.entity}")

          Future(None)
      }
    }.flatMap(identity)
  }

  def applyBillingAddress(token: String, postalCode: String, country: String): Future[Option[Cart]] = {
    val uri = Uri(s"$url/shoppers/me/carts/active/apply-billing-address")
    //https://dispatch-test.digitalriver.com/v1/shoppers/me/carts/active.drivejson?token=c96"
    val qry: Uri.Query = Uri.Query("token" -> token,
      "format" -> "json",
      "postalCode" -> postalCode,
      "country" -> country)

    val request = HttpRequest(HttpMethods.POST, uri = uri.withQuery(qry))

    Http().singleRequest(request).map { response =>
      response.status match {
        case StatusCodes.OK =>
          import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

          val result: Future[CartResponse] = Unmarshal[HttpEntity](response.entity).to[CartResponse]

          val detail = result.map { x => Some(x.cart) }
          result.onFailure {
            case y =>
              log.warn(response.entity.toString)
              log.error(y.getMessage)
          }
          detail

        case _ =>
          log.error(s"URI= ${uri.toString()}")
          log.error(s"Invalid status code of ${response.status} -${response.entity}")
          None
          Future(None)
      }
    }.flatMap(identity)
  }

  def setBillingAddress(token: String, address: Address): Future[Option[Cart]] = {
    val uri = Uri(s"$url/shoppers/me/carts/active/apply-billing-address")
    //https://dispatch-test.digitalriver.com/v1/shoppers/me/carts/active.drivejson?token=c96"
    val qry: Uri.Query = Uri.Query("token" -> token,
      "format" -> "json"
    )

    log.info("XXX - FIXME - convert address to JSON and add it to the body")
    val request = HttpRequest(HttpMethods.PUT, uri = uri.withQuery(qry))

    Http().singleRequest(request).map { response =>
      response.status match {
        case StatusCodes.OK =>
          import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

          val result: Future[CartResponse] = Unmarshal[HttpEntity](response.entity).to[CartResponse]

          val detail = result.map { x => Some(x.cart) }
          result.onFailure {
            case y =>
              log.warn(response.entity.toString)
              log.error(y.getMessage)
          }
          detail

        case _ =>
          log.error(s"URI= ${uri.toString()}")
          log.error(s"Invalid status code of ${response.status} -${response.entity}")
          Future(None)
      }
    }.flatMap(identity)
  }

  // expand=lineItems.lineItem.customAttributes%2ClineItems.lineItem.product.SKU%2CbillingAddress%2CshippingAddress%2CshippingOptions%2CcustomAttributes HTTP
  def addItem(token: String, productIds: Seq[String], promoCode: Option[String] = None, quantity: Long = 1L): Future[Option[Cart]] = {

    val uri = Uri(s"$url/shoppers/me/carts/active")

    val qry = promoCode match {
      case Some(x) => Uri.Query("token" -> token,
        "format" -> "json",
        "productId" -> productIds.mkString(","),
        "quantity" -> quantity.toString,
        "promoCode" -> x)
      case None => Uri.Query("token" -> token,
        "format" -> "json",
        "productId" -> productIds.mkString(","),
        "quantity" -> quantity.toString
      )
    }

    val request = HttpRequest(HttpMethods.POST, uri = uri.withQuery(qry))

    Http().singleRequest(request).map { response =>
      response.status match {
        case StatusCodes.OK =>
        //  log.warn(response.entity.toString)
          import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

          val result: Future[CartResponse] = Unmarshal[HttpEntity](response.entity).to[CartResponse]

          val detail = result.map { x => Some(x.cart) }
          result.onFailure {
            case y =>
              log.warn(response.entity.toString)
              log.error(y.getMessage)
          }
          detail
        //Some("OK")
        case _ =>
          log.error(s"URI= ${uri.toString()}")
          log.error(s"Invalid status code of ${response.status} -${response.entity.toString}")
          Future(None)
      }
    }.flatMap(identity)
  }

  def getCategories(parent:Option[Long]):Future[Either[Category,Seq[CategoryMinimum]]] = {
  //  val uri = Uri(s"$url/shoppers/me/shoppers/me/categories")
    val qry: Uri.Query = Uri .Query("apiKey" -> apiKey, "format" -> "json")
    val uri = parent match {
      case Some(catId:Long) => Uri(s"$url/shoppers/me/categories/$catId")
      case None => Uri( s"$url/shoppers/me/categories")
    }

    val request = HttpRequest(HttpMethods.GET, uri = uri.withQuery(qry))

    Http().singleRequest(request).map { response =>
      response.status match {
        case StatusCodes.OK =>
          import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

          val result: Future[CategoryResponse] = Unmarshal[HttpEntity](response.entity).to[CategoryResponse]

          val detail:Future[Either[Category,Seq[CategoryMinimum]]] = result.map { x =>
            if ( x.categories.isDefined) {
              Right(x.categories.get.category)
            } else {
              Left(x.category.get)
            }
          }
          result.onFailure {
            case y =>
              log.warn(response.entity.toString)
              log.error(y.getMessage)
          }
          detail

        case _ =>
          log.error(s"URI= ${uri.toString()}")
          log.error(s"Invalid status code of ${response.status} -${response.entity}")
          Future(Right( Seq.empty))
      }
    }.flatMap(identity)
  }
  def webCheckout(token: String): Future[Option[String]] = {
    val uri = Uri(s"$url/shoppers/me/carts/active/web-checkout")
    //https://dispatch-test.digitalriver.com/v1/shoppers/me/carts/active.drivejson?token=c96"
    val qry: Uri.Query = Uri.Query("token" -> token, "format" -> "json")

    val request = HttpRequest(HttpMethods.GET, uri = uri.withQuery(qry))

    Http().singleRequest(request).map { response =>
      response.status match {
        case StatusCodes.MovedPermanently =>
          val locationHeader:Option[HttpHeader] =  response.headers.find(_.is("location"))
          locationHeader match {
            case Some(x:HttpHeader) => Some(x.value())
            case None => None
          }
        case StatusCodes.Found =>
          val locationHeader:Option[HttpHeader] =  response.headers.find(_.is("location"))
          locationHeader match {
            case Some(x:HttpHeader) => Some(x.value())
            case None => None
          }

        case _ =>
          log.error(s"URI= ${uri.toString()}")
          log.error(s"Invalid status code of ${response.status} -${response.entity}")

          None
      }
    }
  }
}
