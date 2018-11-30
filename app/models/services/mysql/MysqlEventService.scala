package models.services.mysql

import java.sql.SQLException

import scala.concurrent.Future

import anorm._
import anorm.SqlParser.get
import javax.inject.Inject
import org.joda.time.DateTime
import play.api.db.DBApi

import models._
import models.services._



// TODO use GUIDs dammit

@javax.inject.Singleton
class MysqlEventService @Inject()(userService: UserService, rsoService: RsoService)(dbapi: DBApi)(implicit ec: DatabaseExecutionContext) extends EventService {
  import MysqlEventService._

  private val db = dbapi.database("default")

  private type EventRow = Long ~ String ~ String ~ String ~ DateTime ~ String ~ String ~ String ~ String ~ String ~ Int ~ Long ~ Option[Long]
  private val eventParser: RowParser[EventRow] =
    get[Long]("Event.id") ~
    get[String]("Event.name") ~
    get[String]("Event.description") ~
    get[String]("EventCategory.name") ~
    get[DateTime]("Event.date_time") ~
    get[String]("Event.location_name") ~
    get[String]("Event.location_coords") ~
    get[String]("Event.contact_phone") ~
    get[String]("Event.contact_email") ~
    get[String]("EventVisibility.name") ~
    get[Int]("Event.approved") ~
    get[Long]("Event.owner_id") ~
    get[Option[Long]]("Event.rso_id")
  private val nextIdParser = get[Long]("next_id")
  private val resolveIdsParser =
    get[Int]("category_id") ~
    get[Int]("visibility_id")
  
  private val eventRowConverter: EventRow => Future[Event] = {
    case id ~ name ~ description ~ category ~ dateTime ~ locationName ~ locationCoords ~ contactPhone ~ contactEmail ~ visibilityStr ~ approved ~ ownerId ~ rsoIdOpt =>
      val rsoFuture = rsoIdOpt match {
        case Some(rsoId) => rsoService.retrieve(rsoId).map {
          case rsoOpt @ Some(_) => rsoOpt
          case None => throw new SQLException(s"Failed to find RSO $rsoId")
        }
        case None => Future.successful(None)
      }
      rsoFuture.flatMap { rsoOpt =>
        userService.retrieve(ownerId).map {
          case Some(owner) =>
            val visibility = visibilityStr match {
              case "Public" => EventVisibility.Public
              case "Private" => EventVisibility.Private
              case "RSO" => EventVisibility.Rso
            }
            Event(id, name, description, category, dateTime, locationName, locationCoords, contactPhone, contactEmail, visibility, approved == 1, owner, rsoOpt)
          case _ => throw new SQLException(s"Failed to find user $ownerId")
        }
      }
  }
  private val seqEventRowConverter: Seq[EventRow] => Future[Seq[Event]] = rows => Future.sequence(rows.map(eventRowConverter))
  private val optEventRowConverter: Option[EventRow] => Future[Option[Event]] = row => Future.sequence(row.toList.map(eventRowConverter)).map(_.headOption)
  
  def retrieve(): Future[Seq[Event]] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM Event JOIN EventCategory ON Event.category_id = EventCategory.id JOIN EventVisibility ON Event.visibility_id = EventVisibility.id".as(eventParser.*)
    }
  }.flatMap(seqEventRowConverter)

  def retrieve(id: Long): Future[Option[Event]] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM Event JOIN EventCategory ON Event.category_id = EventCategory.id JOIN EventVisibility ON Event.visibility_id = EventVisibility.id WHERE Event.id = $id".as(eventParser.singleOpt)
    }
  }.flatMap(optEventRowConverter)
  
  def retrieve(locationName: String, time: DateTime): Future[Option[Event]] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM Event JOIN EventCategory ON Event.category_id = EventCategory.id JOIN EventVisibility ON Event.visibility_id = EventVisibility.id WHERE Event.location_name = $locationName AND Event.date_time = ${time.toDate}".as(eventParser.singleOpt)
    }
  }.flatMap(optEventRowConverter)
  
  // TODO update+insert lol
  def save(event: Event): Future[Event] = {
    retrieve(event.id).map { result =>
      db.withConnection { implicit connection =>
        val rowsUpdated = result match {
          case Some(_) => update(event)
          case None    => insert(event)
        }
        rowsUpdated match {
          case 0 => throw new SQLException(s"Failed to save event ${event.id}")
          case _ => event
        }
      }
    }
  }
  
  private def resolveIds(event: Event)(implicit connection: java.sql.Connection): (Int, Int) = {
    val visibility = event.visibility match {
      case EventVisibility.Public => "Public"
      case EventVisibility.Private => "Private"
      case EventVisibility.Rso => "RSO"
    }
  
    val categoryId ~ visibilityId =
      SQL"""
        SELECT
          EventCategory.id AS category_id,
          EventVisibility.id AS visibility_id
        FROM EventCategory
        INNER JOIN EventVisibility ON EventVisibility.name = $visibility
        WHERE EventCategory.name = ${event.category}
      """.as(resolveIdsParser.single)
    
    (categoryId, visibilityId)
  }
  private def update(event: Event)(implicit connection: java.sql.Connection): Int = {
    val (categoryId, visibilityId) = resolveIds(event)
    
    SQL"""
      UPDATE Event
      SET
        name = ${event.name},
        description = ${event.description},
        category = $categoryId,
        date_time = ${event.dateTime.toDate},
        location_name = ${event.locationName},
        location_coords = ${event.locationCoords},
        contact_phone = ${event.contactPhone},
        contact_email = ${event.contactEmail},
        visibility_id = $visibilityId,
        approved = ${if(event.approved) 1 else 0},
        owner_id = ${event.owner.id},
        rso_id = ${event.rso.map(_.id)}
      WHERE id = ${event.id}
    """.executeUpdate()
  }
  private def insert(event: Event)(implicit connection: java.sql.Connection): Int = {
    val (categoryId, visibilityId) = resolveIds(event)
    
    SQL"""
      INSERT INTO Event
      VALUES (
        ${event.id},
        ${event.name},
        ${event.description},
        $categoryId,
        ${event.dateTime.toDate},
        ${event.locationName},
        ${event.locationCoords},
        ${event.contactPhone},
        ${event.contactEmail},
        $visibilityId,
        ${if(event.approved) 1 else 0},
        ${event.owner.id},
        ${event.rso.map(_.id)}
      )
    """.executeInsert()
    1
  }
  
  override def delete(id: Long): Future[Unit] = Future {
    db.withConnection { implicit connection =>
      SQL"DELETE FROM Event WHERE Event.id = $id".execute()
    }
  }
  
  // TODO counter that reserves IDs (or just use GUIDs)
  override def nextId(): Future[Long] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT MAX(Event.id)+1 AS next_id FROM Event".as(nextIdParser.single)
    }
  }
  
  /*def insert(event: Event): Future[Int] = Future {
    db.withConnection { implicit connection =>
      SQL(
        """INSERT INTO Event
          |SELECT (
          |  {id},
          |  {name},
          |  {description},
          |  EventCategory.id,
          |  {dateTime},
          |  {locationName},
          |  {locationCoords},
          |  {contactPhone},
          |  {contactEmail},
          |  EventVisibility.id,
          |  1,
          |  {ownerId},
          |  {rsoId}
          |)
          |FROM EventCategory
          |INNER JOIN EventVisibility ON EventVisibility.name = {visibility}
          |WHERE EventCategory.name = {category}
        """.stripMargin).bind(event).executeUpdate()
    }
  }(ec)*/
}
private[this] object MysqlEventService {
}