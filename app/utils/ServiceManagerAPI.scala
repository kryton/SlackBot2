package utils

import java.text.SimpleDateFormat
import java.util.{Calendar, Date}

import akka.actor.ActorSystem
import akka.actor.Status.Success
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.ask
import akka.stream.ActorMaterializer
import play.api.Logger
import play.api.libs.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

/**
  * Created by iholsman on 10/31/2016.
  */
object ServiceManagerAPI {

  def apply(user: String,
            password: String,
            url: String,
            duration: FiniteDuration = 5.seconds)(implicit actorSystem: ActorSystem, executionContext: ExecutionContext): ServiceManagerAPI = {
    new ServiceManagerAPI(user, password, url, duration)
  }

}

case class IncidentDetail(
                           OpenTime: String,
                           IncidentID: String,
                           Status: String,
                           Title: String,
                           Area: String,
                           AssignmentGroup: String,
                           Category: String,
                           ClosedBy: Option[String],
                           ClosedTime: Option[String],
                           Description: Seq[Option[String]],
                           Impact: String,
                           JournalUpdates: Option[Seq[Option[String]]],
                           OpenedBy: String,
                           Priority: Option[String],
                           ProblemType: String,
                           Service: String,
                           Solution: Option[Seq[Option[String]]],
                           Subarea: String,
                           UpdatedBy: String,
                           UpdatedTime: String,
                           Urgency: String,
                           folder: Option[String]
                         )

case class IncidentReturnResponse(Incident: Option[IncidentDetail], Messages: Seq[String], ReturnCode: Int)

case class IncidentListSingle2Response(Incident: IncidentListSingleResponse)

case class IncidentListSingleResponse(IncidentID: String)

case class IncidentListReturnResponse(Messages: Seq[String], ResourceName: String, ReturnCode: Int, content:Option[Seq[IncidentListSingle2Response]])


trait JsonMarshallers extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val incidentDetailFmt = jsonFormat22(IncidentDetail)
  implicit val incidentReturnResponseProtocol = jsonFormat3(IncidentReturnResponse)

  implicit val incidentListSingleResponse = jsonFormat1(IncidentListSingleResponse)
  implicit val incidentListSingle2Response = jsonFormat1(IncidentListSingle2Response)
  implicit val incidentListReturnResponse = jsonFormat4(IncidentListReturnResponse)
}

object JsonMarshallers extends JsonMarshallers

class ServiceManagerAPI(protected val user: String,
                        protected val password: String,
                        url: String,
                        duration: FiniteDuration = 5.seconds)(implicit actorSystem: ActorSystem,
                                                              executionContext: ExecutionContext) {

  import utils.JsonMarshallers._

  val log = Logger.logger
  implicit val materializer = ActorMaterializer()

  protected val incidentURL = "/incidents/"
  protected val authHeader = Authorization(BasicHttpCredentials(user, password))


  def getDetail(incidentID: String): Future[Option[IncidentDetail]] = {
    val uri = Uri(s"$url$incidentURL$incidentID")

    val request = HttpRequest(HttpMethods.GET, uri = uri, headers = List(authHeader))

    Http().singleRequest(request).map { response =>
      response.status match {
        case StatusCodes.OK =>
          import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

          val detail: Future[IncidentReturnResponse] = Unmarshal[HttpEntity](response.entity).to[IncidentReturnResponse]
          val result = detail.map { x => x.Incident }
          detail.onFailure {
            case y =>
              log.warn(response.entity.toString)
              log.error(y.getMessage)
          }
          result

        case _ =>
          log.error(s"URI= ${uri.toString()}")
          log.error(s"Invalid status code of ${response.status} -${response.entity}")
          None
          Future(None)
      }
    }.flatMap(identity)

  }
  lazy val   sm = new SimpleDateFormat("yyy-MM-dd")

  def getList(days:Int, maxPri: Int, openOnly:Boolean = false ): Future[Seq[String]] = {
    val now = Calendar.getInstance()
    now.add(Calendar.DAY_OF_MONTH,-days)
    val sinceStr =sm.format( now.getTime)

    val incidentsForWeekByPriority = s"/incidents/"
    val queryForWeekByPriority = "OpenTime>\"" + sinceStr + "\" and Priority<=" + s"$maxPri"


    val qry: Uri.Query = Uri.Query("query" -> queryForWeekByPriority)
    val uri = Uri(s"$url$incidentsForWeekByPriority").withQuery(qry)
    log.warn(uri.toString())

    val request = HttpRequest(HttpMethods.GET, uri = uri, headers = List(authHeader))

    Http().singleRequest(request).map { response =>
      response.status match {
        case StatusCodes.OK =>
          import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
          val detail: Future[IncidentListReturnResponse] = Unmarshal[HttpEntity](response.entity).to[IncidentListReturnResponse]
          val result:Future[Seq[String]] = detail.map { x =>
            x.content.map { y: Seq[IncidentListSingle2Response] =>
              y.map {
                _.Incident.IncidentID
              }
            }.getOrElse(Seq.empty)
          }
         // result.map(x => x.foreach(log.warn(_)))
          detail.onFailure {
            case y =>
              log.warn(response.entity.toString)
              log.error(y.getMessage)
          }
          result
        case _ =>
          log.error(s"URI= ${uri.toString()}")
          log.error(s"Invalid status code of ${response.status} -${response.entity}")

          Future(Seq.empty)
      }
    }.flatMap(identity)
  }

}
