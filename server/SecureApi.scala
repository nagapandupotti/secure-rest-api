import akka.actor._
import akka.pattern._
import akka.dispatch._
import java.security.MessageDigest
import util.control.Breaks._
import com.typesafe.config.ConfigFactory
import scala.collection._
import scala.collection.mutable.ArrayBuffer
import akka.actor.Props
import java.util.concurrent._
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import sun.reflect.ReflectionFactory.GetReflectionFactoryAction
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.language.postfixOps
import com.sun.org.apache.xalan.internal.xsltc.compiler.Pattern
import akka.actor.ActorSystem
import java.awt.PageAttributes.MediaType
import spray.http.MediaTypes
import spray.json._
import spray.routing._
import com.sun.xml.internal.bind.v2.runtime.RuntimeUtil.ToStringAdapter
import java.util.Calendar
import spray.httpx._
import spray.httpx.SprayJsonSupport._
import java.nio.file._
import org.apache.commons.codec.binary.Base64
import spray.http.HttpCookie

case class Friend(id: String, name: String)
case class FriendParam(id: String)
case class Profile(id: String, userId: String, var firstName: Option[String]=None, var lastName: Option[String]=None, var email: Option[String]=None, var gender: Option[String]=None, var birthday: Option[String]=None, var bio: Option[String]=None)
case class ProfileParam(var firstName: Option[String]=None, var lastName: Option[String]=None, var email: Option[String]=None, var gender: Option[String]=None, var birthday: Option[String]=None, var bio: Option[String]=None)
case class UserPost(id: String, userId: String, var title: Option[String]=None, var description: Option[String]=None, var createdTime: Option[String]=None)
case class UserPostParam(var title: String, var description: Option[String]=None, var createdTime: Option[String]=None)
case class PagePost(id: String, pageId: String, creatorId: String, var title: Option[String]=None, var description: Option[String]=None, var createdTime: Option[String]=None)
case class PagePostParam(var title: String, var description: Option[String]=None, var createdTime: Option[String]=None)
case class Page(id: String, userId: String, var pagePosts: List[PagePost]=Nil)
//case class User(id: String, var profile: Option[Profile]=None, var page: Option[Page]=None, var friends: List[Friend]=Nil, var userPosts: List[UserPost]=Nil)
case class Picture(id: String, userId: String, var value: String, var title: Option[String]=None, var description: Option[String]=None, var createdTime: Option[String]=None)
case class Album(id: String, userId: String, var title: Option[String]=None, var description: Option[String]=None, var createdTime: Option[String]=None, var pictures: List[Picture]=Nil)
case class User(id: String, var profile: Option[Profile]=None, var page: Option[Page]=None, var friends: List[Friend]=Nil, var userPosts: List[UserPost]=Nil, var userPictures: List[Picture]=Nil, var album: Option[Album]=None)
case class PicturePostParam(var value: String, var title: Option[String]=None, var description: Option[String]=None, var createdTime: Option[String]=None)
case class AlbumPostParam(var value: String, var title: Option[String]=None, var description: Option[String]=None, var createdTime: Option[String]=None)

case class UserDumpParam(var id: String, var profileFirstName: Option[String]=None, var profileLastName: Option[String]=None, var profileEmail: Option[String]=None, var profileGender: Option[String]=None, var profileBirthday: Option[String]=None, var profileBio: Option[String]=None, var pictureValue: Option[String]=None, var pictureTitle: Option[String]=None, var pictureDescription: Option[String]=None, var pictureCreatedTime: Option[String]=None)

object MyJsonProtocol extends DefaultJsonProtocol {
  implicit val friendFormat = jsonFormat2(Friend)
  implicit val friendParamFormat = jsonFormat1(FriendParam)
  implicit val profileFormat = jsonFormat8(Profile)
  implicit val profileParamFormat = jsonFormat6(ProfileParam)
  implicit val userPostFormat = jsonFormat5(UserPost)
  implicit val userPostParamFormat = jsonFormat3(UserPostParam)
  implicit val pagePostParamFormat = jsonFormat3(PagePostParam)
  implicit val pagePostFormat = jsonFormat6(PagePost)
  implicit val pageFormat = jsonFormat3(Page)
  implicit val pictureFormat = jsonFormat6(Picture)
  implicit val albumFormat = jsonFormat6(Album)
  implicit val userFormat = jsonFormat7(User)
  implicit val picturePostParamFormat = jsonFormat4(PicturePostParam)
  implicit val AlbumPostParamFormat = jsonFormat4(AlbumPostParam)
  implicit val UserDumpParamFormat = jsonFormat11(UserDumpParam)
}

import MyJsonProtocol._

object Main extends App with SimpleRoutingApp {
  implicit val system = ActorSystem("secure-api-system", ConfigFactory.parseString(get_config))
  var rnd = scala.util.Random
  var users = collection.mutable.Map[String, User]()
  var userIds = ArrayBuffer[String]()

  // generate random id
  def getRandomId() = {
    scala.util.Random.alphanumeric.take(20).mkString
  }
  
  def get_config() : String = {
    var config = """
    akka {
    loglevel = "OFF"
    log-sent-messages = off
    log-received-messages = off
    logger-startup-timeout = 1000s
    }""" : String
    
    return config
  }
  
  startServer(interface="localhost", port=8080) {
    path("hello") {
      get {
        complete {
          "Hello Spray!"
        }
      }
    } ~
    path("users" / """^[a-zA-Z0-9]*$""".r) { userId =>
      get { // just for reference!!
        respondWithMediaType(MediaTypes.`application/json`) {
          var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId))          
          var act = system.actorOf(Props(new RequestHandler("getUser", params)), name = "act" + getRandomId())          
          ctx => act ! ctx
        }
      }
    } ~
    path("users") { // [one time]
      post {        
        entity(as[UserDumpParam]) { userDumpParam =>        
          var params = collection.mutable.Map[String, Option[String]]("id" -> Some(userDumpParam.id), "profileFirstName" -> userDumpParam.profileFirstName, "profileLastName" -> userDumpParam.profileLastName, "profileEmail" -> userDumpParam.profileEmail, "profileGender" -> userDumpParam.profileGender, "profileBirthday" -> userDumpParam.profileBirthday, "profileBio" -> userDumpParam.profileBio, "pictureTitle" -> userDumpParam.pictureTitle, "pictureDescription" -> userDumpParam.pictureDescription, "pictureCreatedTime" -> userDumpParam.pictureCreatedTime, "pictureValue" -> userDumpParam.pictureValue)
          var act = system.actorOf(Props(new RequestHandler("postUsers", params)), name = "act" + getRandomId())          
          ctx => act ! ctx    
        }
      } 
    } ~ // FRIENDS
    path("users" / """^[a-zA-Z0-9]*$""".r / "randFriend") { userId =>
      get { // any one can see user's friends. no sessionUserId required
        cookie("sessionCookie") { sessionCookie =>
          respondWithMediaType(MediaTypes.`application/json`) {
            var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId))          
            var act = system.actorOf(Props(new RequestHandler("getRandFriend", params)), name = "act" + getRandomId())          
            ctx => act ! ctx           
          }
        }
      } 
    } ~
    path("users" / """^[a-zA-Z0-9]*$""".r / "randNonFriend") { userId =>
      get { // any one can see user's friends. no sessionUserId required
        cookie("sessionCookie") { sessionCookie =>
          respondWithMediaType(MediaTypes.`application/json`) {
            var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId))          
            var act = system.actorOf(Props(new RequestHandler("getRandNonFriend", params)), name = "act" + getRandomId())          
            ctx => act ! ctx           
          }
        }
      } 
    } ~
    path("users" / """^[a-zA-Z0-9]*$""".r / "friends") { userId =>
      get { // any one can see user's friends. no sessionUserId required
        cookie("sessionCookie") { sessionCookie =>
          respondWithMediaType(MediaTypes.`application/json`) {
            var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId))          
            var act = system.actorOf(Props(new RequestHandler("getFriends", params)), name = "act" + getRandomId())          
            ctx => act ! ctx           
          }
        }
      } ~
      post { // user can created only his friends. the friend id passed should be a genuine user.
        entity(as[FriendParam]) { friendParam =>
          cookie("sessionCookie") { sessionCookie =>
            var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId), "sessionUserId" -> Some(sessionCookie.content), "id" -> Some(friendParam.id))
            var act = system.actorOf(Props(new RequestHandler("postFriends", params)), name = "act" + getRandomId())          
            ctx => act ! ctx
            
          }
        }
      }
    } ~
    path("users" / """^[a-zA-Z0-9]*$""".r / "friends" / """^[a-zA-Z0-9]*$""".r) { (userId, id) =>
      get { // any one can see the user's friend. no sessionUserId required
        cookie("sessionCookie") { sessionCookie =>
          respondWithMediaType(MediaTypes.`application/json`) {
            var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId),"id" -> Some(id))          
            var act = system.actorOf(Props(new RequestHandler("getFriend", params)), name = "act" + getRandomId())          
            ctx => act ! ctx
          }
        }
      } ~
      delete { // user is allowed to delete only his friends
        cookie("sessionCookie") { sessionCookie =>
          var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId), "sessionUserId" -> Some(sessionCookie.content), "id" -> Some(id))          
          var act = system.actorOf(Props(new RequestHandler("deleteFriend", params)), name = "act" + getRandomId())          
          ctx => act ! ctx
        }
      }
    } ~ // POSTS
    path("users" / """^[a-zA-Z0-9]*$""".r / "posts") { userId =>
      get { // only user and his friends can see the user's posts
        cookie("sessionCookie") { sessionCookie =>
          respondWithMediaType(MediaTypes.`application/json`) {
            var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId), "sessionUserId" -> Some(sessionCookie.content))          
            var act = system.actorOf(Props(new RequestHandler("getPosts", params)), name = "act" + getRandomId())          
            ctx => act ! ctx
          }
        }
      } ~
      post { // only the user can create posts under his identity. no one else can do this
        entity(as[UserPostParam]) { userPostParam =>
          cookie("sessionCookie") { sessionCookie =>
            respondWithMediaType(MediaTypes.`application/json`) {
              var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId), "sessionUserId" -> Some(sessionCookie.content), "title" -> Some(userPostParam.title), "description" -> userPostParam.description, "createdTime" -> userPostParam.createdTime)
              var act = system.actorOf(Props(new RequestHandler("postPosts", params)), name = "act" + getRandomId())          
              ctx => act ! ctx            
            }
          }
        }
      }
    } ~
    path("users" / """^[a-zA-Z0-9]*$""".r / "posts" / """^[a-zA-Z0-9]*$""".r) { (userId, id) =>
      get { // only and his friends can see the post
        cookie("sessionCookie") { sessionCookie =>
          respondWithMediaType(MediaTypes.`application/json`) {
            var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId), "sessionUserId" -> Some(sessionCookie.content), "id" -> Some(id))          
            var act = system.actorOf(Props(new RequestHandler("getPost", params)), name = "act" + getRandomId())          
            ctx => act ! ctx
          }
        }
      } ~
      put { // user can updated only his post
        parameters("title"?, "description"?) { (title, description) =>
          cookie("sessionCookie") { sessionCookie =>
            var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId), "sessionUserId" -> Some(sessionCookie.content), "id" -> Some(id), "title" -> title, "description" -> description)          
            var act = system.actorOf(Props(new RequestHandler("putPost", params)), name = "act" + getRandomId())          
            ctx => act ! ctx
          }
        }
      } ~
      delete { // user can delete only his post
        cookie("sessionCookie") { sessionCookie =>
          var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId), "sessionUserId" -> Some(sessionCookie.content), "id" -> Some(id))          
          var act = system.actorOf(Props(new RequestHandler("deletePost", params)), name = "act" + getRandomId())          
          ctx => act ! ctx
        }
      }
    } ~ // PROFILE
    path("users" / """^[a-zA-Z0-9]*$""".r / "profile") { userId =>
      get { // anyone can view the user's profile
        cookie("sessionCookie") { sessionCookie =>
          respondWithMediaType(MediaTypes.`application/json`) {
              var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId))          
              var act = system.actorOf(Props(new RequestHandler("getProfile", params)), name = "act" + getRandomId())          
              ctx => act ! ctx
          }
        }
      } ~
      post { // user is allowed to create only his profile [one time]
        parameters("firstName", "lastName"?, "email"?, "gender"?, "birthday"?, "bio"?) { (firstName, lastName, email, gender, birthday, bio) =>
          cookie("sessionCookie") { sessionCookie =>
            var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId), "sessionUserId" -> Some(sessionCookie.content), "firstName" -> Some(firstName), "lastName" -> lastName, "email" -> email, "gender" -> gender, "birthday" -> birthday, "bio" -> bio)          
            var act = system.actorOf(Props(new RequestHandler("postProfile", params)), name = "act" + getRandomId())          
            ctx => act ! ctx
          } 
        }
      } ~
      put { // user is allowed to update only his profile
        entity(as[ProfileParam]) { profileParam =>
          cookie("sessionCookie") { sessionCookie =>
            respondWithMediaType(MediaTypes.`application/json`) {
              var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId), "sessionUserId" -> Some(sessionCookie.content), "firstName" -> profileParam.firstName, "lastName" -> profileParam.lastName, "email" -> profileParam.email, "gender" -> profileParam.gender, "birthday" -> profileParam.birthday, "bio" -> profileParam.bio)
              var act = system.actorOf(Props(new RequestHandler("putProfile", params)), name = "act" + getRandomId())          
              ctx => act ! ctx
            }
          }
        } 
      }
    } ~ // PAGE
    path("users" / """^[a-zA-Z0-9]*$""".r / "page") { userId =>
      get { // any one can view the user's page contents
        respondWithMediaType(MediaTypes.`application/json`) {
          cookie("sessionCookie") { sessionCookie =>
            var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId))          
            var act = system.actorOf(Props(new RequestHandler("getPage", params)), name = "act" + getRandomId())          
            ctx => act ! ctx
          }
        }
      } ~
      post { // one time
        cookie("sessionCookie") { sessionCookie =>          
          var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId), "sessionUserId" -> Some(sessionCookie.content))          
          var act = system.actorOf(Props(new RequestHandler("postPage", params)), name = "act" + getRandomId())          
          ctx => act ! ctx
        } 
      } 
    } ~
    path("users" / """^[a-zA-Z0-9]*$""".r / "page" / "posts") { userId =>
      get { // anyone can view the page posts
        cookie("sessionCookie") { sessionCookie =>
          respondWithMediaType(MediaTypes.`application/json`) {
            var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId))          
            var act = system.actorOf(Props(new RequestHandler("getPagePosts", params)), name = "act" + getRandomId())          
            ctx => act ! ctx
          }
        }
      } ~
      post { // only page owner and his friends are allowed to post on the page
        entity(as[PagePostParam]) { pagePostParam =>
          cookie("sessionCookie") { sessionCookie =>
            respondWithMediaType(MediaTypes.`application/json`) {
              var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId), "sessionUserId" -> Some(sessionCookie.content), "title" -> Some(pagePostParam.title), "description" -> pagePostParam.description, "createdTime" -> pagePostParam.createdTime)
              var act = system.actorOf(Props(new RequestHandler("postPagePosts", params)), name = "act" + getRandomId())          
              ctx => act ! ctx
            }
          } 
        }
      }     
    } ~
    path("users" / """^[a-zA-Z0-9]*$""".r / "page" / "posts" / """^[a-zA-Z0-9]*$""".r) { (userId, id) =>
      get { // anyone can view the page post
        cookie("sessionCookie") { sessionCookie =>
          respondWithMediaType(MediaTypes.`application/json`) {
            var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId), "id" -> Some(id))          
            var act = system.actorOf(Props(new RequestHandler("getPagePost", params)), name = "act" + getRandomId())          
            ctx => act ! ctx
          }
        }
      } ~
      put { // only the post creator can update the pagePost
        parameters("title"?, "description"?) { (title, description) =>
          cookie("sessionCookie") { sessionCookie =>
            var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId), "sessionUserId" -> Some(sessionCookie.content), "id" -> Some(id), "title" -> title, "description" -> description)          
            var act = system.actorOf(Props(new RequestHandler("putPagePost", params)), name = "act" + getRandomId())          
            ctx => act ! ctx
          }
        }        
      } ~
      delete { // only the page owner or the post creator can delete the page post
        cookie("sessionCookie") { sessionCookie =>
          var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId), "sessionUserId" -> Some(sessionCookie.content), "id" -> Some(id))          
          var act = system.actorOf(Props(new RequestHandler("deletePagePost", params)), name = "act" + getRandomId())          
          ctx => act ! ctx
        }
      }
    } ~
    path("users" / """^[a-zA-Z0-9]*$""".r / "pictures") { userId =>
      get { // only user and his friends can see the user's pictures
        cookie("sessionCookie") { sessionCookie => 
          respondWithMediaType(MediaTypes.`application/json`) {
            var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId), "sessionUserId" -> Some(sessionCookie.content))          
            var act = system.actorOf(Props(new RequestHandler("getPictures", params)), name = "act" + getRandomId())          
            ctx => act ! ctx           
          }
        }
      } ~
      post { // only the user can add pictures under his identity. No one else can do this 
        entity(as[PicturePostParam]) { picturePostParam => 
          cookie("sessionCookie") { sessionCookie =>
            var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId), "sessionUserId" -> Some(sessionCookie.content), "value" -> Some(picturePostParam.value), "title" -> picturePostParam.title, "description" -> picturePostParam.description, "createdTime" -> picturePostParam.createdTime)          
            var act = system.actorOf(Props(new RequestHandler("postPictures", params)), name = "act" + getRandomId())          
            ctx => act ! ctx
          }
        }
      }
    } ~
    path("users" / """^[a-zA-Z0-9]*$""".r / "pictures" / """^[a-zA-Z0-9]*$""".r) { (userId, userPicId) =>
      get { //only user and his friends can see the user's pictures
        cookie("sessionCookie") { sessionCookie => 
          respondWithMediaType(MediaTypes.`application/json`) {
            var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId),"userPicId" -> Some(userPicId), "sessionUserId" -> Some(sessionCookie.content))          
            var act = system.actorOf(Props(new RequestHandler("getPicture", params)), name = "act" + getRandomId())          
            ctx => act ! ctx
          }
        }
      }
    } ~
    path("users" / """^[a-zA-Z0-9]*$""".r / "albums") { userId =>
      get { // only user and his friends can see the user's albums
        cookie("sessionCookie") { sessionCookie => 
          respondWithMediaType(MediaTypes.`application/json`) {
            var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId), "sessionUserId" -> Some(sessionCookie.content))          
            var act = system.actorOf(Props(new RequestHandler("getAlbum", params)), name = "act" + getRandomId())          
            ctx => act ! ctx
          }
        }
      }
    } ~
    path("users"/"""^[a-zA-Z0-9]*$""".r/"albums"/"pictures") { userId =>
      get { //only user and his friends will see the pictures of albums
        cookie("sessionCookie") { sessionCookie => 
          respondWithMediaType(MediaTypes.`application/json`) {
            var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId), "sessionUserId" -> Some(sessionCookie.content))          
            var act = system.actorOf(Props(new RequestHandler("getAlbumPictures", params)), name = "act" + getRandomId())          
            ctx => act ! ctx
          }
        }
      } ~   
      post { //only album owner is allowed to post picture in the album
        entity(as[AlbumPostParam]) { albumPostParam =>
          cookie("sessionCookie") { sessionCookie =>
            respondWithMediaType(MediaTypes.`application/json`) {
              var params = collection.mutable.Map[String, Option[String]]("userId" -> Some(userId), "sessionUserId" -> Some(sessionCookie.content), "value" -> Some(albumPostParam.value), "title" -> albumPostParam.title, "description" -> albumPostParam.description, "createdTime" -> albumPostParam.createdTime)          
              var act = system.actorOf(Props(new RequestHandler("postAlbumPictures", params)), name = "act" + getRandomId())          
              ctx => act ! ctx
            }
          }
        } 
      }
    } ~
    path("hi") {
      get {
        parameters("id"?) { id =>
          println("Inside path hi. Params id -> " + id)
          var params = collection.mutable.Map[String, Option[String]]("id" -> id)
          var act = system.actorOf(Props(new RequestHandler("hi", params)), name = "act" + getRandomId())          
          ctx => act ! ctx
        }
      }
    } ~
    path("auth") { // save userId in the cookie
      get {
        parameters("id") { id =>
          setCookie(HttpCookie("sessionCookie", id)) {
            complete("user has been logged in")
          }
        }
      }
    } ~
    path("check-auth") {
      get {
        cookie("sessionCookie") { sessionCookie =>
          complete(s"sessionCookie -> '${sessionCookie.content}'")
        }
      }
    }
  } // startServer  
  
  class RequestHandler(route: String, params: Map[String, Option[String]]) extends Actor {
    def receive = {
      case ctx: RequestContext => 
        ctx.complete {
          //println("Inside actor -> " + self.path.name)
          
          var response = route match {
            case "hi"             => ApiBody.hi(params)
            case "getUser"        => ApiBody.getUser(params)
            case "postUsers"      => ApiBody.postUsers(params)
            
            case "getRandFriend"  => ApiBody.getRandFriend(params) 
            case "getRandNonFriend"  => ApiBody.getRandNonFriend(params)             
            case "getFriends"     => ApiBody.getFriends(params)
            case "postFriends"    => ApiBody.postFriends(params)
            case "getFriend"      => ApiBody.getFriend(params)
            case "deleteFriend"   => ApiBody.deleteFriend(params)
            
            case "getPosts"       => ApiBody.getPosts(params)
            case "postPosts"      => ApiBody.postPosts(params)
            case "getPost"        => ApiBody.getPost(params)
            case "putPost"        => ApiBody.putPost(params)
            case "deletePost"     => ApiBody.deletePost(params)
            
            case "getProfile"     => ApiBody.getProfile(params)
            case "postProfile"    => ApiBody.postProfile(params)
            case "putProfile"     => ApiBody.putProfile(params)
            
            case "getPage"        => ApiBody.getPage(params)
            case "postPage"       => ApiBody.postPage(params)
            case "getPagePosts"   => ApiBody.getPagePosts(params)
            case "postPagePosts"  => ApiBody.postPagePosts(params)
            case "getPagePost"    => ApiBody.getPagePost(params)
            case "putPagePost"    => ApiBody.putPagePost(params)
            case "deletePagePost" => ApiBody.deletePagePost(params)
            
            case "getPictures"    => ApiBody.getPictures(params)
            case "getPicture"     => ApiBody.getPicture(params)
            case "postPictures"   => ApiBody.postPictures(params)
            
            case "getAlbum"           => ApiBody.getAlbum(params)
            case "postAlbumPictures"  => ApiBody.postAlbumPictures(params)

            
            case _ => "Unknown"
          }
          
          response
        }
        
        context.stop(self)
      
      case msg: String   => println("Actor received unknown message '$msg'")   
    }
    
    override def postStop = {
      //println("Stopped actor ->" + self.path.name)
    }
  }
  
  object ApiBody {
    def hi(params: Map[String, Option[String]]): String = {
      if(!params("id").isEmpty) { Thread.sleep(10000000) }
      "Hi!"
    }
    
    def getUser(params: Map[String, Option[String]]): String = {
      try {
        users(params("userId").get).toJson.prettyPrint
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def postUsers(params: Map[String, Option[String]]): String = {
      try {
      var id = params("id").get

      println("[POST USERS] params -> " + params)
      println("Create new user -> id:"+id)
      users += (id -> User(id))
      userIds += id
      
      var i = id.charAt(1)
      
      var randProfileId = "Pr" + i + getRandomId()
      var randPageId    = "Pa" + i + getRandomId()
      var randPicId     = "Pic" + i + getRandomId()
      var randAlbumId   = "Al" + i + getRandomId()
            
      
      // default data from client
      var profile = Profile(randProfileId, id, params("profileFirstName"), params("profileLastName"), params("profileEmail"), params("profileGender"), params("profileBirthday"))
      var page    = Page(randPageId, id)      
      var picture = Picture(randPicId, id, params("pictureValue").get, params("pictureTitle"), params("pictureDescription"), params("pictureCreatedTime"))
      var album   = Album(randAlbumId, id, None, None, None, List(picture))
      
      users      += (id -> User(id, Some(profile), Some(page), Nil, Nil, List(picture), Some(album)))      

      "OK" 
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def getRandFriend(params: Map[String, Option[String]]): String = {
      try{
      var friends = users(params("userId").get).friends
      
      if(!friends.isEmpty) {
        var randomIndex = scala.util.Random.nextInt(friends.length)
        friends(randomIndex).id
      } else {
        ""
      }
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
      
    }
    
    def getRandNonFriend(params: Map[String, Option[String]]): String = {
      try {
      var rnd = scala.util.Random
      var friends = users(params("userId").get).friends
      var nonFriendId = ""
      
      if(friends.length < (userIds.length-1)) { // existing friends should not be >= that total users count
        breakable {
          for(i<-0 to 50){ // random checking only 100 times max
            var randUserId = userIds(rnd.nextInt(userIds.length)) // get random userId     
            var isFriend = friends.exists { f => (f.id == randUserId) } // check if this user is not a friend
            if(!isFriend) {
              nonFriendId = randUserId // return this nonFriendUserId
              break
            }
          } // for
        } // breakable
      } // if
      
      nonFriendId
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def getFriends(params: Map[String, Option[String]]): String = {
      try {
      users(params("userId").get).friends.toJson.prettyPrint
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def postFriends(params: Map[String, Option[String]]): String = {
      try {
      var sessionUserId = params("sessionUserId").get
      var userId        = params("userId").get
      var id            = params("id").get
           
      // fetch the friend corresponding to this id
      var friendObjOption = users.get(id)
                       
      if((userId == sessionUserId)) {
        if(friendObjOption == None) {
          "[ERROR] ILLEGAL FRIEND ID. THERE IS NO USER WITH THIS ID : " + id 
          
        } else {
          // check if the friend is already present
          var isFriend = users(userId).friends.exists { f => (f.id == id) }
          
          if(isFriend) {
            "[ERROR] ILLEGAL OPERATION. USER WITH ID:"+id+" IS ALREADY A FRIEND."
            
          } else {
            var friendObj = friendObjOption.get
            var friendProfile = friendObj.profile.get 
            var userObj = users(userId)
            var userProfile = userObj.profile.get
            
            // make friendship at both the ends
            userObj.friends = Friend(friendObj.id, friendProfile.firstName.get+" "+friendProfile.lastName.getOrElse("")) :: userObj.friends
            friendObj.friends = Friend(userObj.id, userProfile.firstName.get+" "+userProfile.lastName.getOrElse("")) :: friendObj.friends
            
            "OK"
          }
        }
      } else {
        "[ERROR] ACCESS DENIED. YOU CANNOT MODIFY SOMEBODY ELSE'S FRIEND LIST."              
      }
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }   
    }
    
    def getFriend(params: Map[String, Option[String]]): String = {
      try {
      var userId = params("userId").get
      var id = params("id").get
      
      println("[GET FRIEND] userId -> " + userId + ", friendId -> " + id)
      var friend = (users(userId.toString).friends.find { p => (p.id == id) }).get
      friend.toJson.prettyPrint
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def deleteFriend(params: Map[String, Option[String]]): String = {
      try {
      var sessionUserId = params("sessionUserId").get
      var userId        = params("userId").get
      var id            = params("id").get      
      
      println("Deleting friend -> " + id)
            
      // fetch the friend corresponding to this id
      var friendObjOption = users.get(id)
      //println("friendObjOption -> " + friendObjOption)            
      
      if(userId == sessionUserId) {
        if(friendObjOption == None) {
          "ILLEGAL FRIEND ID. THERE IS NO USER WITH THIS ID : " + id 
          
        } else {  
          // check if the friend is already absent
          var isFriend = users(userId).friends.exists { f => (f.id == id) }
          if(!isFriend) {
            "ILLEGAL OPERATION. USER WITH ID:"+id+" IS ANYWAYS NOT A FRIEND."
            
          } else {                  
            // remove friendship at both the ends
            var userFriends = users(userId).friends
            var userFriend = (userFriends.find { p => (p.id == id) }).get
            var userFriendsListBuffer = userFriends.to[collection.mutable.ListBuffer]
            userFriendsListBuffer -= userFriend            
            users(userId).friends = userFriendsListBuffer.toList
            
            var frndFriends = users(id).friends // get the friend object
            var frndFriend = (frndFriends.find { p => (p.id == userId) }).get // the original user's friendList entry inside the friend's object
            var frndFriendsListBuffer = frndFriends.to[collection.mutable.ListBuffer]
            frndFriendsListBuffer -= frndFriend
            users(id).friends = frndFriendsListBuffer.toList
                          
            "OK"
          }
        }
      } else {
        "ACCESS DENIED. YOU CANNOT MODIFY SOMEBODY ELSE'S FRIEND LIST."
      }    
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
     
    def getPosts(params: Map[String, Option[String]]): String = {
      try {
      var sessionUserId = params("sessionUserId").get
      var userId = params("userId").get
      
      println("[GET POSTS] SessionUser: "+sessionUserId+" has requested all posts of userId:" + userId)
      var isFriend = users(userId).friends.exists { f => (f.id == sessionUserId) } // sessionUserId should be a friend of userId
      
      if((userId == sessionUserId) || isFriend) {
        users(userId).userPosts.toJson.prettyPrint
      } else {
        "ACCESS DENIED. YOU NEED TO BE THE USER OR A FRIEND TO SEE THE POSTS.".toJson.prettyPrint
      }    
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def postPosts(params: Map[String, Option[String]]): String = {
      try {
      var sessionUserId = params("sessionUserId").get
      var userId = params("userId").get
      var title = params("title").get
      var description = params("description") // opt
      var createdTime = params("createdTime") // opt
      
      if(userId == sessionUserId) {
        var newPostId =  "UP" + getRandomId()
        println("[POST POSTS] Create new userPost -> id:"+newPostId+", userId:"+userId+", title:"+title+", description:"+description+", createdTime"+createdTime)
        var userPost = UserPost(newPostId, userId, Some(title), Some(description.getOrElse("")), Some(createdTime.getOrElse("")))
        users(userId).userPosts = userPost :: users(userId).userPosts
        
        userPost.toJson.prettyPrint
      } else {
        "[ERROR] ACCESS DENIED. YOU CANNOT POST ON SOMEBODY ELSE'S BEHALF."
      } 
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def getPost(params: Map[String, Option[String]]): String = {
      try {
      var sessionUserId = params("sessionUserId").get
      var userId = params("userId").get
      var id = params("id").get
      
      println("[GET POST] SessionUser: "+sessionUserId+" has requested userPostId:" + id +"of userId:" + userId)
      var isFriend = users(userId).friends.exists { f => (f.id == sessionUserId) } // sessionUserId should be a friend of userId
      
      if((userId == sessionUserId) || isFriend) {
        var userPost = (users(userId).userPosts.find { p => (p.id == id) }).get
        userPost.toJson.prettyPrint
      } else {
        "ACCESS DENIED. YOU NEED TO BE THE USER OR A FRIEND TO SEE THE POSTS.".toJson.prettyPrint
      }
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def putPost(params: Map[String, Option[String]]): String = {
      try {
      var sessionUserId = params("sessionUserId").get
      var userId = params("userId").get
      var id = params("id").get
      var title = params("title")
      var description = params("description")
      
      println("[UPDATE POST]  postId:"+id +" of userId:" + userId)
      var userPost = (users(userId).userPosts.find { p => (p.id == id) }).get  // object reference
      
      if(userPost.userId == "sessionUserId") {       
        if(!title.isEmpty) { userPost.title = title }
        if(!description.isEmpty) { userPost.description = description }

        "OK"
      } else {
        "ACCESS DENIED. YOU CANNOT UPDATE SOMEBODY ELSE'S POST."
      }
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def deletePost(params: Map[String, Option[String]]): String = {
      try {
      var sessionUserId = params("sessionUserId").get
      var userId = params("userId").get
      var id = params("id").get
      
      println("Deleting userPost -> " + id)
      var userPosts = users(userId).userPosts
      var userPost = (userPosts.find { p => (p.id == id) }).get
      
      if(userPost.userId == "sessionUserId") {
        var userPostsListBuffer = userPosts.to[collection.mutable.ListBuffer]
        userPostsListBuffer -= userPost
        users(userId).userPosts = userPostsListBuffer.toList
            
        "OK"
      } else {
        "ACCESS DENIED. YOU CANNOT DELETE SOMEBODY ELSE'S POST."
      }
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def getProfile(params: Map[String, Option[String]]): String = {
      try {
      var userId = params("userId").get
      
      println("[GET PROFILE] User:"+userId)
      var profile = users(userId).profile
      if(profile == None) {
        "".toJson.prettyPrint
      } else {
        profile.get.toJson.prettyPrint
      }    
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def postProfile(params: Map[String, Option[String]]): String = {
      try {
      var sessionUserId = params("sessionUserId").get
      var userId = params("userId").get
      var firstName = params("firstName").get
      var lastName = params("lastName")
      var email = params("email")
      var birthday = params("birthday")
      var bio = params("bio")
      var gender = params("gender")
      
      var newProfileId = "Pr" + userId.charAt(1) + getRandomId()
      println("[POST PROFILE] id:"+newProfileId+", userId:"+userId+", firstName:"+firstName+", lastName:"+lastName+", email:"+email+", birthday:"+birthday+", bio:"+bio+", gender:"+gender)

      if(sessionUserId == userId) {
        var profile = Profile(newProfileId, userId, Some(firstName))
        if(!lastName.isEmpty) { profile.lastName = lastName }
        if(!email.isEmpty)    { profile.email = email }
        if(!gender.isEmpty)   { profile.gender = gender }
        if(!birthday.isEmpty) { profile.birthday = birthday }
        if(!bio.isEmpty)      { profile.bio = bio }
        
        users(userId).profile = Some(profile)

        "OK"
      } else {
        "ACCESS DENIED. YOU CANNOT CREATE SOMEBODY ELSE'S PROFILE"
      }
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def putProfile(params: Map[String, Option[String]]): String = {
      try {
      var sessionUserId = params("sessionUserId").get
      var userId = params("userId").get
      var firstName = params("firstName")
      var lastName = params("lastName")
      var email = params("email")
      var birthday = params("birthday")
      var bio = params("bio")
      var gender = params("gender")

      var profile = users(userId).profile.get
      
      println("[PUT PROFILE] userId:"+userId+", firstName:"+firstName+", lastName:"+lastName+", email:"+email+", birthday:"+birthday+", bio:"+bio+", gender:"+gender)

      if(profile.userId == sessionUserId) {
        if(!firstName.isEmpty) { profile.firstName = firstName }
        if(!lastName.isEmpty) { profile.lastName = lastName }
        if(!email.isEmpty) { profile.email = email }
        if(!gender.isEmpty) { profile.gender = gender }
        if(!birthday.isEmpty) { profile.birthday = birthday }
        if(!bio.isEmpty) { profile.bio = bio }
      
        profile.toJson.prettyPrint
      } else {
        "[ERROR] ACCESS DENIED. YOU CANNOT UPDATE SOMEBODY ELSE'S PROFILE."
      }
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def getPage(params: Map[String, Option[String]]): String = {
      try {
      var userPage = users(params("userId").get).page
      if(userPage == None) {
        "".toJson.prettyPrint
      } else {
        userPage.get.toJson.prettyPrint
      } 
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def postPage(params: Map[String, Option[String]]): String = {
      try {
      var sessionUserId = params("sessionUserId").get
      var userId = params("userId").get
      
      var newPageId = "Pa" + getRandomId()
      println("[POST PAGE] id:"+newPageId+", userId:"+userId)

      if(sessionUserId == userId) {
        var page = Page(newPageId, userId)               
        users(userId).page = Some(page)

        "OK"
      } else {
        "ACCESS DENIED. YOU CANNOT CREATE SOMEBODY ELSE'S PAGE"
      }
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def getPagePosts(params: Map[String, Option[String]]): String = {
      try {
      var userPage = users(params("userId").get).page.get
      userPage.pagePosts.toJson.prettyPrint
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def postPagePosts(params: Map[String, Option[String]]): String = {
      try {
      var sessionUserId = params("sessionUserId").get
      var userId = params("userId").get
      var title = params("title").get //opt
      var description = params("description") //opt
      var createdTime = params("createdTime") //opt
      
      var newPostId = "PP" + getRandomId()
      
      // check if creatorId is user's friend
      var isFriend = users(userId).friends.exists { f => (f.id == sessionUserId) }
      
      //println("isFriend -> " + isFriend)
 
      if((sessionUserId == userId) || isFriend) {
        println("[POST PAGE-POST] id:"+newPostId+", userId:"+userId+", creatorId:"+sessionUserId+", title:"+title+", description:"+description+", createdTime"+createdTime)
        
        var userPage = users(userId).page.get
        var pagePost = PagePost(newPostId, userId, sessionUserId, Some(title), Some(description.getOrElse("")), Some(createdTime.getOrElse("")))
        userPage.pagePosts =  pagePost :: userPage.pagePosts

        pagePost.toJson.prettyPrint
      } else {
        "[ERROR] ACCESS DENIED. YOU NEED TO BE THE PAGE OWNER OR A FRIEND TO POST ON THIS PAGE."
      }    
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def getPagePost(params: Map[String, Option[String]]): String = {
      try {
      var userId = params("userId").get
      var id = params("id").get
      
      println("[GET PAGE-POST] id:"+id+"of User:"+userId)
      var userPage = users(userId).page.get
      var pagePost = (userPage.pagePosts.find { p => (p.id == id) }).get
      
      pagePost.toJson.prettyPrint
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def putPagePost(params: Map[String, Option[String]]): String = {
      try {
      var sessionUserId = params("sessionUserId").get
      var userId = params("userId").get
      var id = params("id").get
      var title = params("title")
      var description = params("description") //opt
      
      println("[PUT PAGE-POST] SessionUserId:"+sessionUserId+" wants to update the pagePost -> "+id +" of user -> " + userId)
      var userPage = users(userId).page.get
      var pagePost = (userPage.pagePosts.find { p => (p.id == id) }).get
      
      if(pagePost.creatorId == sessionUserId) {
        if(!title.isEmpty) { pagePost.title = title }
        if(!description.isEmpty) { pagePost.description = description }

        "OK"
      } else {
        "ACCESS DENIED. YOU NEED TO BE THE CREATOR OF THIS POST."
      }
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def deletePagePost(params: Map[String, Option[String]]): String = {
      try {
      var sessionUserId = params("sessionUserId").get
      var userId = params("userId").get
      var id = params("id").get
      
      println("Deleting pagePost -> " + id)
      var userPage = users(userId).page.get
      var pagePosts = userPage.pagePosts
      var pagePost = (pagePosts.find { p => (p.id == id) }).get
      
      if((userPage.userId == userId) || (pagePost.creatorId == sessionUserId)) {
        var pagePostsListBuffer = pagePosts.to[collection.mutable.ListBuffer]
        pagePostsListBuffer -= pagePost
        userPage.pagePosts = pagePostsListBuffer.toList

        "OK"
      } else{
        "ACCESS DENIED. YOU NEED TO BE THE PAGE OWNER OR THE CREATOR OF THIS POST."  
      }    
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
 
    def getPicture(params: Map[String, Option[String]]): String = {
      try {
      var sessionUserId = params("sessionUserId").get
      var userId = params("userId").get
      var userPicId = params("userPicId").get
      
      println("Get userPicId -> " + userPicId +"of userId -> " + userId)
      
      //check if creatorId is user's friend
      var isFriend = users(userId).friends.exists { f => (f.id == sessionUserId) }
      
      if((userId == sessionUserId) || isFriend) {
        var userPicture = (users(userId).userPictures.find { p => (p.id == userPicId) }).get
        userPicture.toJson.prettyPrint
        
      } else {
        "[ERROR] ACCESS DENIED. YOU NEED TO BE THE USER OR A FRIEND TO SEE THE PICTURE.".toJson.prettyPrint
      }
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }        
      }
    }   
    
    def getPictures(params: Map[String, Option[String]]): String = {
      try {
      var userId = params("userId").get
      var sessionUserId = params("sessionUserId").get
     
      println("Get all pictures for userId -> " + userId)   
      //check if creatorId is user's friend
      var isFriend = users(userId).friends.exists { f => (f.id == sessionUserId) }
      
      if((userId == sessionUserId) || isFriend) {
        users(userId).userPictures.toJson.prettyPrint
      } else { 
        //println("[ERROR] ACCESS DENIED. YOU NEED TO BE THE USER OR A FRIEND TO SEE THE PICTURES.")
        "[ERROR] ACCESS DENIED. YOU NEED TO BE THE USER OR A FRIEND TO SEE THE PICTURES.".toJson.prettyPrint
      }
      } catch {
        case t: Throwable => {
          
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def postPictures(params: Map[String, Option[String]]): String = {
      try {
      var sessionUserId = params("sessionUserId").get
      var userId = params("userId").get
      var value = params("value").get
      var title = params("title")
      var description = params("description")
      var createdTime = params("createdTime")
      
      if(userId == sessionUserId) {
        var newPictureId = "Pic" + getRandomId()
        println("Create new userPicture -> id:"+newPictureId+", userId:"+userId+", value: <HIDDEN>, title:"+title+", description:"+description+", createdTime"+createdTime)
        var picture = Picture(newPictureId, userId, value, Some(title.getOrElse("")), Some(description.getOrElse("")), Some(createdTime.getOrElse("")))
        users(userId).userPictures = picture :: users(userId).userPictures
        
        picture.toJson.prettyPrint

      } else {
        //println("[ERROR] ACCESS DENIED. YOU CANNOT POST PICTURE ON SOMEBODY ELSE'S BEHALF.")
        "[ERROR] ACCESS DENIED. YOU CANNOT POST PICTURE ON SOMEBODY ELSE'S BEHALF."
      }
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }
    
    def getAlbum(params: Map[String, Option[String]]): String = {
      try {
      var sessionUserId = params("sessionUserId").get
      var userId = params("userId").get
      
      var userAlbum = users(userId).album
      println("[GET ALBUM] userId -> " + userId)   
      var isFriend = users(userId).friends.exists { f => (f.id == sessionUserId) }
      
      if((userId == sessionUserId) || isFriend) {
        userAlbum.toJson.prettyPrint
      } else { 
        "[ERROR] ACCESS DENIED. YOU NEED TO BE THE USER OR A FRIEND TO SEE THE ALBUM.".toJson.prettyPrint
      }
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      } 
    }
    
    def postAlbumPictures(params: Map[String, Option[String]]): String = {
      try {
      var sessionUserId = params("sessionUserId").get
      var userId = params("userId").get
      var value = params("value").get
      var title = params("title")
      var description = params("description")
      var createdTime = params("createdTime")
     
      var newPictureId = "AP" + getRandomId()
      if(userId == sessionUserId){
        println("[POST ALBUM PICTURES] Create new Picture -> id:"+newPictureId+", userId:"+userId+", creatorId:"+sessionUserId+", value: <HIDDEN>, title:"+title+", description:"+description+", createdTime"+createdTime)
        
        var userAlbum = users(userId).album.get
        var picture = Picture(newPictureId, userId, value, Some(title.getOrElse("")), Some(description.getOrElse("")), Some(createdTime.getOrElse("")))
        userAlbum.pictures = picture :: userAlbum.pictures

        picture.toJson.prettyPrint
        
      } else {
          "[ERROR] ACCESS DENIED. YOU NEED TO BE THE ALBUM OWNER TO POST PICTURE IN THIS ALBUM."
      }    
      } catch {
        case t: Throwable => {
         "[ERROR] error while executing request." 
        }
      }
    }

    
  } // object ApiBody
} // object Main

