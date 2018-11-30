package models.services.mysql

import java.sql.SQLException

import javax.inject.Inject

import scala.concurrent.Future

import play.api.db.DBApi
import anorm._
import anorm.SqlParser.{ get, str }
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.PasswordInfo
import play.api.libs.json
import play.api.libs.json.Json

import models._
import models.services._

@javax.inject.Singleton
class MysqlUserService @Inject()(universityService: UniversityService, dbapi: DBApi)(implicit ec: DatabaseExecutionContext) extends UserService {
  import MysqlUserService._
  
  private val db = dbapi.database("default")
  
  private type UserRow = Long ~ String ~ String ~ Option[Long] ~ String ~ Option[String] ~ String
  private val userParser =
    get[Long]("User.id") ~
        get[String]("User.username") ~
        get[String]("User.email") ~
        get[Option[Long]]("User.university_id") ~
        get[String]("User.login_info") ~
        get[Option[String]]("User.password_info") ~
        get[String]("Role.name")
  private val nextIdParser = get[Long]("next_id")
  private val resolveIdsParser = get[Int]("role_id")
  
  private val userRowConverter: UserRow => Future[User] = {
    case id ~ username ~ email ~ universityIdOpt ~ loginInfo ~ passwordInfo ~ roleName =>
      val universityFuture = universityIdOpt match {
        case Some(universityId) => universityService.retrieve(universityId).map {
          case universityOpt @ Some(_) => universityOpt
          case None => throw new SQLException(s"Failed to find university $universityId")
        }
        case None => Future.successful(None)
      }
      universityFuture.map { universityOpt =>
        User(id, username, email, universityOpt, readJson[LoginInfo](loginInfo), passwordInfo.map(readJson[PasswordInfo]), roleName match {
          case "Student" => Role.Student
          case "Admin" => Role.Admin
          case "Super Admin" => Role.SuperAdmin
        })
      }
  }
  
  private val seqUserRowConverter: Seq[UserRow] => Future[Seq[User]] = rows => Future.sequence(rows.map(userRowConverter))
  private val optUserRowConverter: Option[UserRow] => Future[Option[User]] = row => Future.sequence(row.toList.map(userRowConverter)).map(_.headOption)
  
  
  override def retrieve(): Future[Seq[User]] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM User".as(userParser.*)
    }
  }.flatMap(seqUserRowConverter)
  
  override def retrieve(id: Long): Future[Option[User]] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM User JOIN Role ON User.role_id = Role.id WHERE User.id = $id".as(userParser.singleOpt)
    }
  }.flatMap(optUserRowConverter)
  
  override def retrieve(loginInfo: LoginInfo): Future[Option[User]] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM User JOIN Role ON User.role_id = Role.id WHERE User.login_info = ${writeJson(loginInfo)}".as(userParser.singleOpt)
    }
  }.flatMap(optUserRowConverter)
  
  override def retrieve(username: String): Future[Option[User]] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT * FROM User JOIN Role ON User.role_id = Role.id WHERE User.username = $username".as(userParser.singleOpt)
    }
  }.flatMap(optUserRowConverter)
  
  // TODO update+insert lol
  def save(user: User): Future[User] = {
    retrieve(user.id).map { result =>
      db.withConnection { implicit connection =>
        val rowsUpdated = result match {
          case Some(_) => update(user)
          case None    => insert(user)
        }
        rowsUpdated match {
          case 0 => throw new SQLException(s"Failed to save user ${user.id}")
          case _ => user
        }
      }
    }
  }
  
  private def resolveIds(user: User)(implicit connection: java.sql.Connection): Int = {
    val role = user.role match {
      case Role.Student => "Student"
      case Role.Admin => "Admin"
      case Role.SuperAdmin => "Super Admin"
    }
  
    val userId =
      SQL"""
        SELECT
          Role.id AS role_id
        FROM Role
        WHERE Role.name = $role
      """.as(resolveIdsParser.single)
    
    userId
  }
  private def update(user: User)(implicit connection: java.sql.Connection): Int = {
    val roleId = resolveIds(user)
    
    SQL"""
      UPDATE User
      SET
        username = ${user.username},
        email = ${user.email},
        university_id = ${user.university.map(_.id)},
        login_info = ${writeJson[LoginInfo](user.loginInfo)},
        password_info = ${user.passwordInfo.map(writeJson[PasswordInfo])},
        role_id = $roleId
      WHERE id = ${user.id}
    """.executeUpdate()
  }
  private def insert(user: User)(implicit connection: java.sql.Connection): Int = {
    val roleId = resolveIds(user)
  
    SQL"""
      INSERT INTO User
      VALUES (
        ${user.id},
        ${user.username},
        ${user.email},
        ${user.university.map(_.id)},
        ${writeJson[LoginInfo](user.loginInfo)},
        ${user.passwordInfo.map(writeJson[PasswordInfo])},
        $roleId
      )
    """.executeInsert()
    1
  }
  
  override def nextId(): Future[Long] = Future {
    db.withConnection { implicit connection =>
      SQL"SELECT MAX(User.id)+1 AS next_id FROM User".as(nextIdParser.single)
    }
  }
}
private[this] object MysqlUserService {
  implicit val loginInfoFormat: json.OFormat[LoginInfo] = Json.format
  implicit val passwordFormat: json.OFormat[PasswordInfo] = Json.format
  
  def readJson[T](string: String)(implicit reads: json.Reads[T]) = json.JsResult.toTry(Json.fromJson(Json.parse(string))(reads)).get
  def writeJson[T](o: T)(implicit writes: json.Writes[T]) = Json.stringify(Json.toJson(o)(writes))
}