package models.services.mysql

import java.sql.SQLException

import anorm._
import anorm.SqlParser.get
import javax.inject.Inject
import models._
import models.services._
import play.api.db.DBApi

import scala.concurrent.Future

@javax.inject.Singleton
class MysqlEventCommentService @Inject()(eventService: EventService, userService: UserService, dbapi: DBApi)(implicit ec: DatabaseExecutionContext) extends EventCommentService {
  
  private val db = dbapi.database("default")
  
  private type EventCommentRow = Long ~ Long ~ Long ~ Int ~ String
  private val commentParser: RowParser[EventCommentRow] =
    get[Long]("EventComment.id") ~
        get[Long]("EventComment.event_id") ~
        get[Long]("EventComment.user_id") ~
        get[Int]("EventComment.rating") ~
        get[String]("EventComment.body")
  private val nextIdParser = get[Long]("next_id")
  
  private val commentRowConverter: EventCommentRow => Future[EventComment] = {
    case id ~ eventId ~ userId ~ rating ~ body =>
      eventService.retrieve(eventId).flatMap {
        case Some(event) =>
          userService.retrieve(userId).map {
            case Some(user) => EventComment(id, event, user, rating, body)
            case _ => throw new SQLException(s"Failed to find user $userId")
          }
        case _ => throw new SQLException(s"Failed to find event $eventId")
      }
  }
  private val seqEventCommentRowConverter: Seq[EventCommentRow] => Future[Seq[EventComment]] = rows => Future.sequence(rows.map(commentRowConverter))
  private val optEventCommentRowConverter: Option[EventCommentRow] => Future[Option[EventComment]] = row => Future.sequence(row.toList.map(commentRowConverter)).map(_.headOption)
  
  override def retrieve(id: Long): Future[Option[EventComment]] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM EventComment WHERE EventComment.id = $id".as(commentParser.singleOpt)
    }
  }.flatMap(optEventCommentRowConverter)
  
  override def retrieve(event: Event): Future[Seq[EventComment]] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM EventComment WHERE EventComment.event_id = ${event.id}".as(commentParser.*)
    }
  }.flatMap(seqEventCommentRowConverter)
  
  override def retrieve(user: User): Future[Seq[EventComment]] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM EventComment WHERE EventComment.user_id = ${user.id}".as(commentParser.*)
    }
  }.flatMap(seqEventCommentRowConverter)
  
  
  // TODO update+insert lol
  override def save(comment: EventComment): Future[EventComment] = {
    retrieve(comment.id).map { result =>
      db.withConnection { implicit connection =>
        val rowsUpdated = result match {
          case Some(_) => update(comment)
          case None    => insert(comment)
        }
        rowsUpdated match {
          case 0 => throw new SQLException(s"Failed to save comment ${comment.id}")
          case _ => comment
        }
      }
    }
  }
  private def update(comment: EventComment)(implicit connection: java.sql.Connection): Int = {
    SQL"""
      UPDATE EventComment
      SET
        event_id = ${comment.event.id},
        user_id = ${comment.user.id},
        rating = ${comment.rating},
        body = ${comment.body}
      WHERE id = ${comment.id}
    """.executeUpdate()
  }
  private def insert(comment: EventComment)(implicit connection: java.sql.Connection): Int = {
    SQL"""
      INSERT INTO EventComment
      VALUES (
        ${comment.id},
        ${comment.event.id},
        ${comment.user.id},
        ${comment.rating},
        ${comment.body}
      )
    """.executeInsert()
    1
  }
  
  override def delete(comment: EventComment): Future[Unit] = Future {
    db.withConnection { implicit connection =>
      SQL"DELETE FROM EventComment WHERE EventComment.id = ${comment.id}".execute()
    }
  }
  
  override def nextId(): Future[Long] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT MAX(EventComment.id)+1 AS next_id FROM EventComment".as(nextIdParser.single)
    }
  }
}
