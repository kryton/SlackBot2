package models

import java.sql.Date
import javax.inject.Inject

import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile

import scala.annotation.tailrec
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.Duration
/**
  * Created by iholsman on 9/19/2016.
  */


case class SlackSession( teamId: String, token: String)

class SlackSessionRepo  @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) {
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  val db = dbConfig.db

  import dbConfig.driver.api._

  val tableQuery = TableQuery[SlackSessionTable]
  class SlackSessionTable(tag: Tag) extends Table[SlackSession](tag, "SLACKSESSION") {
    def teamId = column[String]("TEAMID", O.PrimaryKey)
    def token = column[String]("TOKEN")

    def * = (teamId, token) <> ((SlackSession.apply _).tupled, SlackSession.unapply)
  }
/*
  def createIt()(implicit executionContext: ExecutionContext): Unit = {
    try {
      import slick.jdbc.meta.MTable

      def createTableIfNotInTables(tables: Vector[MTable]): Future[Unit] = {
        if (!tables.exists(_.name.name == tableQuery.baseTableRow.tableName)) {
          db.run(tableQuery.schema.create)
        } else {
          Future()
        }
      }
      val createTableIfNotExist: Future[Unit] = db.run(MTable.getTables).flatMap(createTableIfNotInTables)

      Await.result(createTableIfNotExist, Duration.Inf)
    } finally db.close
   // db.run( DBIO.seq(tableQuery.schema.create))
  }
  */
  def truncate() = {
    db.run(tableQuery.delete)
  }

  def all():Future[List[SlackSession]] = {
    db.run(tableQuery.to[List].result)
  }


  def findByToken(token: String): Future[Option[SlackSession]] = {
    db.run( tableQuery.filter(_.token === token).result.headOption)
  }
  def findByTeamId(id: String): Future[Option[SlackSession]] = {
    db.run( tableQuery.filter(_.teamId === id).result.headOption)
  }

  /**
    * Delete a specific entity by id. If successfully completed return 1. otherwise 0
    */
  def deleteByToken(token: String): Future[Int] = db.run( tableQuery.filter(_.token === token ).delete )
  def deleteByTeamId(id: String): Future[Int] = db.run( tableQuery.filter(_.teamId === id ).delete )

  def create(teamId:String, token:String): Unit = {
    Logger.logger.info(s"Saving Token for team $teamId")
    val obj = SlackSession(teamId, token)
    db.run(
      DBIO.seq(
        tableQuery.filter(_.teamId === teamId).delete,
        tableQuery += obj)
    )
  }

}
