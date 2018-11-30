package controllers

import javax.inject.Inject

import scala.concurrent.{ ExecutionContext, Future }
import play.api.data.Forms._
import play.api.data._
import play.api.i18n._
import play.api.mvc._
import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.util.{ Credentials, PasswordHasherRegistry, PasswordInfo }
import com.mohiva.play.silhouette.impl.exceptions.IdentityNotFoundException
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import org.joda.time.DateTime
import models._
import models.services._
import org.h2.tools.Server
import play.api.Configuration
import utils.auth.DefaultEnv

import scala.concurrent.duration.FiniteDuration



class HomeController @Inject()(silhouette: Silhouette[DefaultEnv],
    universityService: UniversityService,
    userService: UserService,
    eventService: EventService,
    rsoService: RsoService,
    rsoMembershipService: RsoMembershipService,
    eventCommentService: EventCommentService,
    credentialsProvider: CredentialsProvider,
    passwordHasherRegistry: PasswordHasherRegistry,
    cc: MessagesControllerComponents)(implicit ec: ExecutionContext)
  extends MessagesAbstractController(cc) {
  
  import HomeController._
  
  startH2Server()

  private val logger = play.api.Logger(this.getClass)

  /**
    * This result directly redirect to the application home.
    */
  val Home = Redirect(routes.HomeController.myEventsCalendar())
  
  val eventForm: EventForm = Form(
    tuple(
      "rso_id" -> text,
      "name" -> text,
      "description" -> text,
      "date_time" -> nonEmptyText,
      "location_name" -> text,
      "location_coords" -> text,
      "contact_phone" -> text,
      "contact_email" -> text,
      "visibility" -> nonEmptyText
    )
  )
  
  val commentForm: CommentForm = Form(
    tuple(
      "rating" -> text,
      "body" -> text
    )
  )
  
  val registerForm: RegisterForm = Form(
    tuple(
      "username" -> text,
      "email" -> text,
      "university_id" -> text,
      "password" -> text
    )
  )
  val loginForm: LoginForm = Form(
    tuple(
      "username" -> text,
      "password" -> text
    )
  )
  
  val rsoForm: RsoForm = Form(
    single(
      "name" -> text
    )
  )

  // -- Actions

  /**
    * Handle default path requests, redirect to computers list
    */
  def index = silhouette.UserAwareAction { implicit request =>
    request.identity match {
      case Some(_) => Home
      case None => Redirect(routes.HomeController.login())
    }
  }
  
  def myEventsCalendar = silhouette.SecuredAction.async { implicit request: UserRequest =>
    withConflictingEvent { conflictingEvent =>
      rsoService.retrieve(request.identity).flatMap { ownedRsoList =>
        eventService.retrieve().map { events =>
          Ok(views.html.CalendarEventsPage(SidebarSelection.MyEventsCalendar, events, eventForm, ownedRsoList, conflictingEvent))
        }
      }
    }
  }
  def myEventsMap = silhouette.SecuredAction.async { implicit request: UserRequest =>
    withConflictingEvent { conflictingEvent =>
      rsoService.retrieve(request.identity).flatMap { ownedRsoList =>
        eventService.retrieve().map { events =>
          Ok(views.html.MapEventsPage(SidebarSelection.MyEventsMap, events, eventForm, ownedRsoList, conflictingEvent))
        }
      }
    }
  }
  def allEventsCalendar = silhouette.SecuredAction.async { implicit request: UserRequest =>
    withConflictingEvent { conflictingEvent =>
      rsoService.retrieve(request.identity).flatMap { ownedRsoList =>
        eventService.retrieve().map { events =>
          Ok(views.html.CalendarEventsPage(SidebarSelection.AllEventsCalendar, events, eventForm, ownedRsoList, conflictingEvent))
        }
      }
    }
  }
  def allEventsMap = silhouette.SecuredAction.async { implicit request: UserRequest =>
    withConflictingEvent { conflictingEvent =>
      rsoService.retrieve(request.identity).flatMap { ownedRsoList =>
        eventService.retrieve().map { events =>
          Ok(views.html.MapEventsPage(SidebarSelection.AllEventsMap, events, eventForm, ownedRsoList, conflictingEvent))
        }
      }
    }
  }
  private def withConflictingEvent(handler: Option[Event] => Future[Result])(implicit request: UserRequest) =
    request.session.get("error_conflictingEvent") match {
      case Some(eventIdStr) =>
        eventService.retrieve(eventIdStr.toInt).flatMap { conflictingEvent =>
          handler(conflictingEvent).map(_.withSession(request.session - "error_conflictingEvent"))
        }
      case _ =>
        handler(None)
    }
  def clubs = silhouette.SecuredAction.async { implicit request: UserRequest =>
    rsoService.retrieve().flatMap { rsoList =>
      rsoMembershipService.retrieve(request.identity).map { userRsoList =>
        Ok(views.html.Clubs(SidebarSelection.Clubs, rsoList, userRsoList.map(_.rso), rsoForm))
      }
    }
  }
  def event(id: Long) = silhouette.SecuredAction.async { implicit request: UserRequest =>
    eventService.retrieve(id).flatMap {
      case Some(event) =>
        eventCommentService.retrieve(event).map { comments =>
          Ok(views.html.Event(SidebarSelection.None, event, comments, commentForm))
        }
      case None => Future.successful(NotFound("Event not found"))
    }
  }
  def createComment(id: Long) = silhouette.SecuredAction.async { implicit request: UserRequest =>
    commentForm.bindFromRequest.fold(
      formWithErrors => Future.successful(Ok(formWithErrors.errors.mkString(", "))),
      form => {
        val (ratingStr, body) = form
        eventService.retrieve(id).flatMap {
          case Some(event) =>
            eventCommentService.nextId().flatMap { nextId =>
              eventCommentService.save(EventComment(nextId, event, request.identity, ratingStr.toInt, body)).map { _ =>
                Redirect(routes.HomeController.event(id))
              }
            }
          case None => Future.successful(NotFound("Event not found"))
        }
      }
    )
  }
  def editComment(id: Long) = silhouette.SecuredAction.async { implicit request: UserRequest =>
    commentForm.bindFromRequest.fold(
      formWithErrors => Future.successful(Ok(formWithErrors.errors.mkString(", "))),
      form => {
        val (ratingStr, body) = form
        eventCommentService.retrieve(id).flatMap {
          case Some(comment) =>
            eventCommentService.save(EventComment(id, comment.event, request.identity, ratingStr.toInt, body)).map { _ =>
              Redirect(routes.HomeController.event(comment.event.id))
            }
          case None => Future.successful(NotFound("Event not found"))
        }
      }
    )
  }
  def deleteComment(id: Long) = silhouette.SecuredAction.async { implicit request: UserRequest =>
    eventCommentService.retrieve(id).flatMap {
      case Some(comment) =>
        eventCommentService.delete(comment).map(_ => Ok)
    }
  }
  def createEvent = silhouette.SecuredAction.async { implicit request: UserRequest =>
    eventForm.bindFromRequest.fold(
      formWithErrors => Future.successful(Ok(formWithErrors.errors.mkString(", "))),
      form => {
        val (rsoIdStr, name, description, dateTimeStr, locationName, locationCoords, contactPhone, contactEmail, visibilityStr) = form
        val dateTime = DateTime.parse(dateTimeStr)
        val visibility = visibilityStr match {
          case "Public" => EventVisibility.Public
          case "Private" => EventVisibility.Private
          case "RSO" => EventVisibility.Rso
        }
        val eventConflict =
          if(locationName.trim != "")
            eventService.retrieve(locationName, dateTime)
          else Future.successful(None)
        eventConflict.flatMap {
          case Some(conflictingEvent) =>
            Future.successful(Redirect(routes.HomeController.myEventsCalendar()).withSession("error_conflictingEvent" -> conflictingEvent.id.toString))
          case None =>
            rsoService.retrieve(rsoIdStr.toLong).flatMap {
              case Some(rso) if request.identity.role != Role.SuperAdmin && rso.owner != request.identity =>
                Future.failed(new IllegalAccessException("Attempted to create event for RSO that was not authorized"))
              case Some(rso) =>
                eventService.nextId().flatMap { nextId =>
                  val event = Event(nextId, name, description, "Misc", dateTime, locationName, locationCoords, contactPhone, contactEmail, visibility, approved = true, request.identity, Some(rso))
                  eventService.save(event).map { _ =>
                    Redirect(routes.HomeController.myEventsCalendar())
                  }
                }
              case None =>
                Future.successful(NotFound("RSO not found"))
            }
        }
      }
    )
  }
  
  def login = silhouette.UnsecuredAction { implicit request =>
    Ok(views.html.Login(loginForm))
  }
  def performLogin = silhouette.UnsecuredAction.async { implicit request =>
    loginForm.bindFromRequest.fold(
      form => Future.successful(BadRequest(views.html.Login(form))),
      data => {
        val (username, password) = data
        
        userService.retrieve(username).flatMap {
          case Some(user) if user.passwordInfo.forall(passwordHasherRegistry.current.matches(_, password)) =>
            silhouette.env.authenticatorService.create(user.loginInfo).flatMap { authenticator =>
              silhouette.env.eventBus.publish(LoginEvent(user, request))
              silhouette.env.authenticatorService.init(authenticator).flatMap { v =>
                silhouette.env.authenticatorService.embed(v, Home)
              }
            }
          case Some(_) => Future.failed(new IdentityNotFoundException("Wrong password"))
          case None => Future.failed(new IdentityNotFoundException("Couldn't find user"))
        }
      }
    )
  }
  def register = silhouette.UnsecuredAction.async { implicit request =>
    universityService.retrieve().map { universities =>
      Ok(views.html.Register(universities, registerForm))
    }
  }
  def performRegistration = silhouette.UnsecuredAction.async { implicit request =>
    registerForm.bindFromRequest.fold(
      formWithErrors => Future.successful(Ok(formWithErrors.errors.mkString(", "))),
      form => {
        val (username, email, universityId, password) = form
        universityService.retrieve(universityId.toLong).flatMap {
          case university @ Some(_) =>
            userService.nextId().flatMap { nextId =>
              val loginInfo = LoginInfo(CredentialsProvider.ID, nextId.toString)
              val passwordInfo = passwordHasherRegistry.current.hash(password)
              userService.save(User(nextId, username, email, university, loginInfo, Some(passwordInfo), Role.Student)).flatMap { user =>
                silhouette.env.eventBus.publish(SignUpEvent(user, request))
      
                silhouette.env.authenticatorService.create(loginInfo).flatMap { authenticator =>
                  silhouette.env.eventBus.publish(LoginEvent(user, request))
                  silhouette.env.authenticatorService.init(authenticator).flatMap { v =>
                    silhouette.env.authenticatorService.embed(v, Home)
                  }
                }
              }
            }
          case None => throw new Exception("University not found")
        }
      }
    )
  }
  def logout = silhouette.SecuredAction.async { implicit request: UserRequest =>
    silhouette.env.eventBus.publish(LogoutEvent(request.identity, request))
    silhouette.env.authenticatorService.discard(request.authenticator, Redirect(routes.HomeController.login()))
  }
  
  def userProfile = silhouette.SecuredAction { implicit request: UserRequest =>
    Ok(views.html.Profile(SidebarSelection.UserProfile))
  }
  def updateUserProfile = silhouette.SecuredAction { implicit request: UserRequest =>
    Home
  }
  
  def joinRso(rsoId: Long) = silhouette.SecuredAction.async { implicit request: UserRequest =>
    rsoService.retrieve(rsoId).flatMap {
      case Some(rso) =>
        rsoMembershipService.save(RsoMembership(rso, request.identity, approved = false)).flatMap { _ =>
          updateRsoStatus(rso).map(_ => Ok)
        }
      case None => Future.successful(NotFound)
    }
  }
  def leaveRso(rsoId: Long) = silhouette.SecuredAction.async { implicit request: UserRequest =>
    rsoService.retrieve(rsoId).flatMap {
      case Some(rso) =>
        rsoMembershipService.retrieve(rso, request.identity).flatMap {
          case Some(membership) =>
            rsoMembershipService.delete(membership).flatMap { _ =>
              updateRsoStatus(rso).map(_ => Ok)
            }
          case None => Future.successful(Ok)
        }
      case None => Future.successful(NotFound)
    }
  }
  private def updateRsoStatus(rso: Rso) =
    rsoMembershipService.retrieve(rso).flatMap { members =>
      if(members.size < 5)
        rsoService.save(rso.copy(approved = false))
      else
        rsoService.save(rso.copy(approved = true))
    }
  def createRso = silhouette.SecuredAction.async { implicit request: UserRequest =>
    rsoForm.bindFromRequest.fold(
      formWithErrors => Future.successful(Ok(formWithErrors.errors.mkString(", "))),
      rsoName => {
        rsoService.nextId().flatMap { nextId =>
          val rso = Rso(nextId, rsoName, request.identity, request.identity.university.get, approved = false)
          rsoService.save(rso).flatMap { _ =>
            rsoMembershipService.save(RsoMembership(rso, request.identity, approved = true)).map(_ => Redirect(routes.HomeController.clubs()))
          }
        }
      }
    )
  }
  def deleteRso(rsoId: Long) = silhouette.SecuredAction.async { implicit request: UserRequest =>
    rsoService.retrieve(rsoId).flatMap {
      case Some(rso) =>
        rsoService.delete(rso).map(_ => Ok)
      case None => Future.successful(NotFound)
    }
  }
  def editRso(rsoId: Long) = silhouette.SecuredAction.async { implicit request: UserRequest =>
    rsoForm.bindFromRequest.fold(
      formWithErrors => Future.successful(Ok(formWithErrors.errors.mkString(", "))),
      rsoName => {
        rsoService.save(Rso(rsoId, rsoName, request.identity, request.identity.university.get, approved = true)).map(_ => Redirect(routes.HomeController.clubs()))
      }
    )
  }
  
  
  /*
  def login = Action.async { implicit request =>
    Future.successful(Ok(loginTemplate()))
  }
  
  def upcomingEvents = Action.async { implicit request =>
    eventService.list.map { events =>
      val now = DateTime.now()
      Ok(eventsTemplate(HomeController.EventsTab.Upcoming, events.filter(_.dateTime.isAfter(now)), eventForm))
    }
  }

//  def upcomingEvents = Action.async { implicit request =>
//    userService.findByUsername("admin").map {
//      case Some(user) => Ok(eventsTemplate(user))
//      case None => NotFound
//    }
//  }
  
  def pastEvents = Action.async { implicit request =>
    eventService.list.map { events =>
      val now = DateTime.now()
      Ok(eventsTemplate(HomeController.EventsTab.Past, events.filter(_.dateTime.isBefore(now)), eventForm))
    }
  }
  
  def ownEvents = Action.async { implicit request =>
    userService.findByUsername("marco").flatMap {
      case Some(user) =>
        eventService.list.map { events =>
          Ok(eventsTemplate(HomeController.EventsTab.Own, events.filter(_.ownerId == user.id), eventForm))
        }
      case None => Future.successful(NotFound)
    }
  }
  
  def createEvent = Action.async { implicit request =>
    eventForm.bindFromRequest.fold(
      formWithErrors => Future.successful(BadRequest),
      form => {
        val (name, description, dateTime, locationName, locationCoords, contactPhone, contactEmail, visibility, owner) = form
        userService.findByUsername(owner).flatMap {
          case Some(user) =>
            val event = Event((math.random*Long.MaxValue).toLong, name, description, "Test", DateTime.parse(dateTime), locationName, locationCoords, contactPhone, contactEmail, visibility, true, user.id, None)
            eventService.insert(event).map { _ =>
              Redirect(routes.HomeController.ownEvents)
            }
          case None => Future.successful(NotFound)
        }
        
      }
    )
  }
  */
}

object HomeController {
  
  type UserRequest = SecuredRequest[DefaultEnv, AnyContent]
  
  def startH2Server(): Unit = {
    try {
      val webServer: Server = Server.createWebServer("-web", "-webAllowOthers", "-webPort", "8082").start
      val server: Server = Server.createTcpServer("-tcp", "-tcpAllowOthers", "-tcpPort", "9092").start
    } catch {
      case e: Exception =>
    }
  }
  
  type EventForm = Form[(String, String, String, String, String, String, String, String, String)]
  type CommentForm = Form[(String, String)]
  type RegisterForm = Form[(String, String, String, String)]
  type LoginForm = Form[(String, String)]
  type RsoForm = Form[String]
  
  sealed trait SidebarSelection
  object SidebarSelection {
    case object UserProfile extends SidebarSelection
    case object MyEventsList extends SidebarSelection
    case object MyEventsCalendar extends SidebarSelection
    case object MyEventsMap extends SidebarSelection
    case object AllEventsList extends SidebarSelection
    case object AllEventsCalendar extends SidebarSelection
    case object AllEventsMap extends SidebarSelection
    case object Clubs extends SidebarSelection
    case object None extends SidebarSelection
  }
}
