package spray.http

import spray.client.pipelining._
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
import spray.json._
import spray.routing._
import com.sun.xml.internal.bind.v2.runtime.RuntimeUtil.ToStringAdapter
import java.util.Calendar
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import spray.httpx._
import spray.httpx.SprayJsonSupport
import spray.json.AdditionalFormats
import java.nio.file._
import org.apache.commons.codec.binary.Base64
import scala.annotation.{ implicitNotFound, tailrec }
import scala.collection.mutable.ListBuffer

import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher

import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.BadPaddingException
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

case class Friend(id: String, name: String)
case class Profile(id: String, userId: String, var firstName: Option[String]=None, var lastName: Option[String]=None, var email: Option[String]=None, var gender: Option[String]=None, var birthday: Option[String]=None, var bio: Option[String]=None)
case class UserPost(id: String, userId: String, var title: Option[String]=None, var description: Option[String]=None, var createdTime: Option[String]=None)
case class PagePost(id: String, pageId: String, creatorId: String, var title: Option[String]=None, var description: Option[String]=None, var createdTime: Option[String]=None)
case class Page(id: String, userId: String, var pagePosts: List[PagePost]=Nil)
case class Picture(id: String, userId: String, var value: String, var title: Option[String]=None, var description: Option[String]=None, var createdTime: Option[String]=None)
case class Album(id: String, userId: String, var title: Option[String]=None, var description: Option[String]=None, var createdTime: Option[String]=None, var pictures: List[Picture]=Nil)
case class User(id: String, var profile: Option[Profile]=None, var page: Option[Page]=None, var friends: List[Friend]=Nil, var userPosts: List[UserPost]=Nil)
case class FriendList(friends: List[Friend]=Nil)
case class UserPostList(var userPosts: List[UserPost]=Nil)
case class PictureList(var pictures: List[Picture]=Nil)

case class ActorInfo(id: String, ref: ActorRef, pubKey: PublicKey)

// actor message case classes
case class InitUser()
case class InitFriend(id: String)
case class Simulator()

case class GetPost(post: UserPost, aesKeyEncrypted: String, aesIvEncrypted: String)
case class GetPosts(userId: String, httpCookie: List[HttpCookie])
case class CreatePost(userId: String, httpCookie: List[HttpCookie], aesKey: SecretKey, aesIv: Array[Byte])
case class GetPostsFromServer(userId: String, pubKey: PublicKey)
case class UserPostListEncrypt(userPostList: UserPostList, aesKey: String, aesIv: String)

case class GetPage(userId: String, httpCookie: List[HttpCookie])
case class GetPagePost(pagePost: PagePost, aesKeyEncrypted: String, aesIvEncrypted: String)
case class CreatePagePost(userId: String, httpCookie: List[HttpCookie], aesKey: SecretKey, aesIv: Array[Byte])
case class GetPageFromServer(userId: String, pubKey: PublicKey)
case class PageEncrypt(page: Page, aesKey: String, aesIv: String)

case class GetFriend(userId: String, friendUserId: String)
case class GetFriends(userId: String, httpCookie: List[HttpCookie])
case class GetRandomFriend(userId: String, httpCookie: List[HttpCookie])
case class GetRandomNonFriend(userId: String, httpCookie: List[HttpCookie])
case class AddFriend(friendUserId: String, userId: String, httpCookie: List[HttpCookie])

case class GetProfile(profile: Profile, aesKeyEncrypted: String, aesIvEncrypted: String)
case class GetProfile2(userId: String, httpCookie: List[HttpCookie])
case class PutProfile(userId: String, httpCookie: List[HttpCookie], aesKey: SecretKey, aesIv: Array[Byte])
case class GetProfileFromServer(userId: String, pubKey: PublicKey)
case class ProfileEncrypt(profile: Profile, aesKey: String, aesIv: String)

case class GetPicture(picture: Picture, aesKeyEncrypted: String, aesIvEncrypted: String)
case class GetPictures(userId: String, httpCookie: List[HttpCookie])
case class PostPicture(userId: String, httpCookie: List[HttpCookie], aesKey: SecretKey, aesIv: Array[Byte])
case class GetPicturesFromServer(userId: String, pubKey: PublicKey)
case class PictureListEncrypt(pictureList: PictureList, aesKey: String, aesIv: String)

case class GetAlbum(userId: String)
case class GetAlbum2(userId: String, httpCookie: List[HttpCookie])
case class PostAlbumPicture(userId: String, httpCookie: List[HttpCookie], aesKey: SecretKey, aesIv: Array[Byte])
case class GetAlbumFromServer(userId: String, pubKey: PublicKey)
case class AlbumEncrypt(album: Album, aesKey: String, aesIv: String)

case class AuthUser() // clientActor
case class Auth(userId: String) // activityActor
case class CheckAuth(userId: String, httpCookie: List[HttpCookie]) //activityActor

object MyJsonProtocol extends DefaultJsonProtocol {
  implicit val friendFormat = jsonFormat2(Friend)
  implicit val profileFormat = jsonFormat8(Profile)
  implicit val userPostFormat = jsonFormat5(UserPost)
  implicit val pagePostFormat = jsonFormat6(PagePost)
  implicit val pageFormat = jsonFormat3(Page)
  implicit val pictureFormat = jsonFormat6(Picture)
  implicit val albumFormat = jsonFormat6(Album)
  implicit val userFormat = jsonFormat5(User)
  implicit object friendListJsonFormat extends RootJsonFormat[FriendList] {
    def read(value: JsValue) = FriendList(value.convertTo[List[Friend]])
    def write(f: FriendList) = f.friends.toJson
    
  }
  implicit object userPostListJsonFormat extends RootJsonFormat[UserPostList] {
    def read(value: JsValue) = UserPostList(value.convertTo[List[UserPost]])
    def write(f: UserPostList) = f.userPosts.toJson
    
  }
  implicit object pictureListJsonFormat extends RootJsonFormat[PictureList] {
    def read(value: JsValue) = PictureList(value.convertTo[List[Picture]])
    def write(f: PictureList) = f.pictures.toJson
    
  }
}

object Aes {
  
  val AES_KEYLENGTH = 128
  
  def genRandKey(): SecretKey = {
    var keyGenAES = KeyGenerator.getInstance("AES")
    keyGenAES.init(128)
    return keyGenAES.generateKey()
  }
  
  def genRandIv(): Array[Byte] = {
    var aesIv = new Array[Byte](AES_KEYLENGTH/8)
    var prng = new SecureRandom()
    prng.nextBytes(aesIv)
    return aesIv
  }
  
  def encrypt(key: SecretKey, iv: Array[Byte], plainText: String): String = {   
    var cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
    cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv))
    var encryptedByteArray = cipher.doFinal(plainText.getBytes())
    var encryptedText =  Base64.encodeBase64String(encryptedByteArray)
    return encryptedText
  }
  
  def decrypt(key: SecretKey, iv: Array[Byte], encryptedText: String): String = {
    var cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
    cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv))
    var plainText = cipher.doFinal(Base64.decodeBase64(encryptedText))
    return new String(plainText)
  }
  
  
}

object Rsa {
  
  def genKeys = {
    var keyGenRSA = KeyPairGenerator.getInstance("RSA")
    keyGenRSA.initialize(1024)
    var key       = keyGenRSA.generateKeyPair()   
    var pubKey    = key.getPublic()
    var priKey    = key.getPrivate()
    
    (pubKey, priKey)
  }
  
  def encrypt(pubKey: PublicKey, plainText: String): String = {
    var cipher = Cipher.getInstance("RSA")
    cipher.init(Cipher.ENCRYPT_MODE, pubKey)
    var encryptedByteArray = cipher.doFinal(plainText.getBytes())
    var encryptedText = Base64.encodeBase64String(encryptedByteArray)
    return encryptedText
  }

  def decrypt(encryptedText: String,  priKey:PrivateKey): String = {
      var cipher = Cipher.getInstance("RSA");
      cipher.init(Cipher.DECRYPT_MODE, priKey);
      var decryptedText = cipher.doFinal(Base64.decodeBase64(encryptedText));
      return new String(decryptedText);
  }  
  
}

import MyJsonProtocol._


object TimeOut {
  var t = new Timeout(Duration.create(5, "seconds"))
}

object Cancellables {
  var arr = ArrayBuffer[ActorRef]()
}

object Main extends App {
  
  // generate random id
  def getRandomId() = {
    scala.util.Random.alphanumeric.take(20).mkString
  }
  
  def getRandUser(): String = userIds(scala.util.Random.nextInt(userIds.length))

  def createClientActors = {
    println("Inside createClientActors ...")
      
    users.foreach {
      case(userId, defaultUserObj) =>
  
        // GENERATE PUB AND PRIV KEYS
        var keys = Rsa.genKeys
        var pubKey = keys._1
        var priKey = keys._2
        
        // GENERATE PERSONAL AES KEY and IV (Not Shared with anyone)
        var aesKey = Aes.genRandKey()
        var aesIv = Aes.genRandIv()
        
        println("pubKeyStr -> " + pubKey + "\n\npriKeyStr -> " + priKey + "\n\n aesKey -> " + aesKey + "\n\n aesIv -> " + aesIv)
        
        // Create new actor
        var clientActor = actorSystem.actorOf(Props(new ClientActor(pubKey, priKey, aesKey, aesIv)), name = userId)
        
        // map actor data to global var
        var actorInfo = new ActorInfo(userId, clientActor, pubKey)
        actorInfos += (userId -> actorInfo)
        userIds += userId
        
        println("[INITIALIZATION] Dumping Client Actor Data of:" + userId + " at the server.")
          
        var futureInitUser = Patterns.ask(clientActor, InitUser, Timeout(Duration.create(5, "seconds")))
        var res = Await.result(futureInitUser, Duration.create(5, "seconds"))  
        
        var futureAuthUser = Patterns.ask(clientActor, AuthUser, Timeout(Duration.create(5, "seconds")))
        var responseAuthUser = Await.result(futureAuthUser, Duration.create(5, "seconds"))
  
    }
      
    println("Finished adding default user data! Adding friends ...")
    Thread.sleep(2000)
      
    actorInfos.foreach {
      case(userId, actorInfo) =>
        var clientActor = actorInfo.ref
        println("userId:"+userId+", clientActor:"+clientActor)
        
        var friends = users(userId).friends
        
        if(!friends.isEmpty) {
          friends.foreach {
            friend =>
              println("[INITIALIZATION] Adding default friend:"+friend.id+ " to clientActor:"+ userId)
              try {
                var futureInitFriend = Patterns.ask(clientActor, InitFriend(friend.id), Timeout(Duration.create(5, "seconds")))
                var res = Await.result(futureInitFriend, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
                
              } catch {
                case t: Throwable => {
                  //println("ERROR inside GET Album executed by Actor:" + self.path.name)
                }
              }
          }          
        } else {
          println("userId:"+userId+" has not friends.")
        }
    }
      
    Thread.sleep(3000)
    println()
    println("Finished adding default friends.")

  }
  
  def assignSimulator = {
    println("Assigning Simulator to Actors ...")
    actorInfos.foreach {
      case(userId, actorInfo) =>
        println("[INITIALIZATION] Hooking Simulator to Actor:"+userId)
        actorInfo.ref ! Simulator
      
    }    
  }

  def populateUsers = {
    for(i<- 0 to (USER_COUNT-1)) {
      var randUserId    = "U" + i + getRandomId()
      var randProfileId = getRandomId() // not used
      var randPageId    = getRandomId() // not used
      
      var userFirstName = "User" + i
      var userLastName  = "Scalabot"
     
      var profile = Profile(randProfileId, randUserId, Some(userFirstName), Some(userLastName))
      
      users      += (randUserId -> User(randUserId, Some(profile))) 
    }    
    
    // populate default user friends
    var users_arr = users.toArray
    for(j<-0 to (users_arr.length-1)) {
      var userObj = users_arr(j)._2
      var userFriends = userObj.friends
      var userProfile = userObj.profile.get
      
      var friendsMax = 5
      if(users_arr.length < 5) { friendsMax = users_arr.length }
      
      var friendsPerUser = scala.util.Random.nextInt(friendsMax) // number of friends for this user      
      println("UserId:"+userObj.id + ", # of friends = " + friendsPerUser)
      
      if(friendsPerUser > 0) {
        var startIndex = scala.util.Random.nextInt(users_arr.length)
        for(k<-startIndex to (startIndex + friendsPerUser - 1)) {
          var friendIndex = k % users_arr.length
          
          if(friendIndex != j) { // friend is not the same as the current user
            var friendObj = users_arr(friendIndex)._2
            
            // add to friendList only if the friendObj is not present
            var isFriend = userFriends.exists { f => (f.id == friendObj.id) }           
            if(!isFriend) {
              var friendProfile = friendObj.profile.get
              
              // add as friends at both the userObj end and friendObj end
              userObj.friends = Friend(friendObj.id, friendProfile.firstName.get+" "+friendProfile.lastName.getOrElse("")) :: userObj.friends
              friendObj.friends = Friend(userObj.id, userProfile.firstName.get+" "+userProfile.lastName.getOrElse("")) :: friendObj.friends
            }
          }  
        } // for
      } // if (friendsPerUser ...)
    } // for(i<-0 ...)
  } // def populate_users
  
  
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
  
  val USER_COUNT = args(0).toInt
  
  if(USER_COUNT <= 1) {
    println("\nInvalid parameter! User count should be > 1. Eg: sbt \"run 10\"")
    System.exit(1)
  }  
    
  // Simulation Frequency (every 'x' seconds)
  val CREATE_POST_FREQ        = 5
  val GET_POSTS_FREQ          = 5
  val CREATE_PAGE_POSTS_FREQ  = 5
  val GET_PAGE_FREQ           = 10
  val PUT_PROFILE_FREQ        = 7
  val GET_PROFILE_FREQ        = 10
  val POST_FRIEND_FREQ        = 15
  val GET_FRIENDS_FREQ        = 15
  val POST_PICTURE_FREQ       = 7
  val GET_PICTURES_FREQ       = 10
  val POST_ALBUM_PICTURE_FREQ = 6
  val GET_ALBUM_FREQ          = 10
  
  var rnd = scala.util.Random
  val actorSystem = ActorSystem("secure-client-system")//, ConfigFactory.parseString(get_config)) //TODO: uncomment
  var users = collection.mutable.Map[String, User]()
  var userIds = ArrayBuffer[String]()
  var actorInfos = collection.mutable.Map[String, ActorInfo]()
  var busyWait = collection.mutable.Map[String, String]()
  
  populateUsers
  createClientActors
  assignSimulator
  
//  test TODO: Remove
//  var key = actorInfos.keys.head
//  var act = actorInfos(key).ref
//  act ! Simulator

  class ActivityActor extends Actor {
    import context._
    import system.dispatcher // execution context for futures
    implicit val system = context.system
    implicit val timeout: Timeout = Timeout(Duration.create(1000, "seconds"))
    var pipeline: HttpRequest => Future[HttpResponse] = sendReceive
    
    def receive = {
      case CreatePost(userId, httpCookie, aesKey, aesIv) =>      
        var title = Aes.encrypt(aesKey, aesIv, "Sample Post Title " + getRandomId())
        var description = Aes.encrypt(aesKey, aesIv, "Sample Description Text " + getRandomId())
        var createdTime = Aes.encrypt(aesKey, aesIv, Calendar.getInstance().getTime().toString)  
        var postData = s"""{ "title":"$title", "description":"$description", "createdTime":"$createdTime" }"""
        var req = Post("http://localhost:8080/users/"+userId+"/posts", HttpEntity(MediaTypes.`application/json`, postData)) ~> addHeader(spray.http.HttpHeaders.Cookie(httpCookie))
        pipeline(req).pipeTo(sender)
        
      case CreatePagePost(userId, httpCookie, aesKey, aesIv) =>      
        var title = Aes.encrypt(aesKey, aesIv, "Sample Page Post Title " + getRandomId())
        var description = Aes.encrypt(aesKey, aesIv, "Sample Page Post Description " + getRandomId())
        var createdTime = Aes.encrypt(aesKey, aesIv, Calendar.getInstance().getTime().toString  )
        var postData = s"""{ "title":"$title", "description":"$description", "createdTime":"$createdTime" }"""
        var req = Post("http://localhost:8080/users/"+userId+"/page/posts", HttpEntity(MediaTypes.`application/json`, postData)) ~> addHeader(spray.http.HttpHeaders.Cookie(httpCookie))
        pipeline(req).pipeTo(sender)

      case PutProfile(userId, httpCookie, aesKey, aesIv) =>
        var i = scala.util.Random.nextInt(3) // select at Random which field to update
        var postData = ""
        
        i match {
          case 0 =>
            println("Updating lastName of userId:"+userId)
            var lastName = "lastName"+getRandomId()
            postData = s"""{ "lastName":"$lastName" }"""
            
          case 1 =>
            println("Updating email of userId:"+userId)
            var email = Aes.encrypt(aesKey, aesIv, "email"+getRandomId() + "@ufl.edu")
            postData = s"""{ "email":"$email" }"""
            
          case 2 =>
            println("Updating bio of userId:"+userId)
            var bio = Aes.encrypt(aesKey, aesIv, "Someting interesting about me and some junk text "+getRandomId() +".")
            postData = s"""{ "bio":"$bio" }"""
            
          case _ => 
            postData = s"""{}"""
            
        }
            
        println("postData -> " + postData)
        var req = Put("http://localhost:8080/users/"+userId+"/profile", HttpEntity(MediaTypes.`application/json`, postData)) ~> addHeader(spray.http.HttpHeaders.Cookie(httpCookie))
        pipeline(req).pipeTo(sender)
        
      case PostPicture(userId, httpCookie, aesKey, aesIv) =>
        var imgsrc = Array("image1.jpg", "image2.jpg", "image3.jpg")
        var randomid = scala.util.Random.nextInt(imgsrc.length)
        val srcArray = Files.readAllBytes(Paths.get(imgsrc(randomid))) 
        var value = Aes.encrypt(aesKey, aesIv, Base64.encodeBase64String(srcArray).toString())
        
        var title = Aes.encrypt(aesKey, aesIv, "Sample Image Title Text " + getRandomId())
        var description = Aes.encrypt(aesKey, aesIv, "Sample Image Description Text " + getRandomId())
        var createdTime = Aes.encrypt(aesKey, aesIv, Calendar.getInstance().getTime().toString)  
        var postData = s"""{ "title":"$title", "description":"$description", "createdTime":"$createdTime", "value":"$value" }"""
        
        println("postData -> " + postData)
        var req = Post("http://localhost:8080/users/"+userId+"/pictures", HttpEntity(MediaTypes.`application/json`, postData)) ~> addHeader(spray.http.HttpHeaders.Cookie(httpCookie))
        pipeline(req).pipeTo(sender)
        
      case PostAlbumPicture(userId, httpCookie, aesKey, aesIv) =>
        // fetch images from local directory
        var imgsrc = Array("image1.jpg", "image2.jpg", "image3.jpg")
        var randomid = scala.util.Random.nextInt(imgsrc.length)
        val srcArray = Files.readAllBytes(Paths.get(imgsrc(randomid))) 
        var value = Aes.encrypt(aesKey, aesIv, Base64.encodeBase64String(srcArray).toString())
        
        var title = Aes.encrypt(aesKey, aesIv, "Sample Album Image Title Text " + getRandomId())
        var description = Aes.encrypt(aesKey, aesIv, "Sample Album Image Description Text " + getRandomId())
        var createdTime = Aes.encrypt(aesKey, aesIv, Calendar.getInstance().getTime().toString)  
        
        var postData = s"""{ "title":"$title", "description":"$description", "createdTime":"$createdTime", "value":"$value" }"""
        
        println("postData -> " + postData)
        var req = Post("http://localhost:8080/users/"+userId+"/albums/pictures", HttpEntity(MediaTypes.`application/json`, postData)) ~> addHeader(spray.http.HttpHeaders.Cookie(httpCookie))
        pipeline(req).pipeTo(sender)
        
          
      case AddFriend(friendUserId, userId, httpCookie) =>
        var postData = s"""{ "id":"$friendUserId" }"""
        var req = Post("http://localhost:8080/users/"+userId+"/friends", HttpEntity(MediaTypes.`application/json`, postData)) ~> addHeader(spray.http.HttpHeaders.Cookie(httpCookie))
        pipeline(req).pipeTo(sender)
        
      case GetRandomFriend(userId, httpCookie) =>
        var req = Get("http://localhost:8080/users/"+userId+"/randFriend") ~> addHeader(spray.http.HttpHeaders.Cookie(httpCookie))
        pipeline(req).pipeTo(sender)
        
      case GetRandomNonFriend(userId, httpCookie) =>
        var req = Get("http://localhost:8080/users/"+userId+"/randNonFriend") ~> addHeader(spray.http.HttpHeaders.Cookie(httpCookie))
        pipeline(req).pipeTo(sender)
        
      case GetFriends(userId, httpCookie) =>
        var req = Get("http://localhost:8080/users/"+userId+"/friends") ~> addHeader(spray.http.HttpHeaders.Cookie(httpCookie))
        pipeline(req).pipeTo(sender)
        
      case GetProfile2(userId, httpCookie) =>
        var req = Get("http://localhost:8080/users/"+userId+"/profile") ~> addHeader(spray.http.HttpHeaders.Cookie(httpCookie))
        pipeline(req).pipeTo(sender)  
        
      case GetPage(userId, httpCookie) =>
        var req = Get("http://localhost:8080/users/"+userId+"/page") ~> addHeader(spray.http.HttpHeaders.Cookie(httpCookie))
        pipeline(req).pipeTo(sender)

      case GetPosts(userId, httpCookie) =>
        var req = Get("http://localhost:8080/users/"+userId+"/posts") ~> addHeader(spray.http.HttpHeaders.Cookie(httpCookie))
        pipeline(req).pipeTo(sender)
        
      case GetPictures(userId, httpCookie) =>
        var req = Get("http://localhost:8080/users/"+userId+"/pictures") ~> addHeader(spray.http.HttpHeaders.Cookie(httpCookie))
        pipeline(req).pipeTo(sender)
        
      case GetAlbum2(userId, httpCookie) =>  
        var req = Get("http://localhost:8080/users/"+userId+"/albums") ~> addHeader(spray.http.HttpHeaders.Cookie(httpCookie))
        pipeline(req).pipeTo(sender)          
        
      case Auth(userId) => 
        pipeline(Get("http://localhost:8080/auth?id="+userId)).pipeTo(sender) // user is not authenticated during this request
        
      case CheckAuth(userId, httpCookie) =>
        var req = Get("http://localhost:8080/check-auth") ~> addHeader(spray.http.HttpHeaders.Cookie(httpCookie)) 
        pipeline(req).pipeTo(sender)

    } // receive 
  } // ActivityActor 

    
  class ClientActor(pubKey: PublicKey, priKey: PrivateKey, aesKey: SecretKey, aesIv: Array[Byte]) extends Actor {
    import context._
    import system.dispatcher // execution context for futures
    implicit val system = context.system
    implicit val timeout: Timeout = Timeout(Duration.create(1000, "seconds"))
    var pipeline: HttpRequest => Future[HttpResponse] = sendReceive
    var httpCookie: List[HttpCookie] = _
    var publicKey: PublicKey = pubKey
    var privateKey: PrivateKey = priKey
    var personalAESKey: SecretKey = aesKey
    var personalAESIv: Array[Byte] = aesIv
    
    // Initialize user data
    def initUser = {  
      var userId = self.path.name
      var i = userId.charAt(1)
      
      var profileFirstName = "User" + i
      var profileLastName  = "Scalabot"
      var profileEmail     = Aes.encrypt(personalAESKey, personalAESIv, profileFirstName.toLowerCase() + "@ufl.edu")
      var gender    = if ((i%2) == 0) "male" else "female"
      var profileGender = Aes.encrypt(personalAESKey, personalAESIv, gender)
      var monthRange       = 1 to 12; var dayRange = 1 to 30;
      var profileBirthday  = Aes.encrypt(personalAESKey, personalAESIv, rnd.nextInt(monthRange length) + "/" + rnd.nextInt(dayRange length) + "/2015") // mm/dd/yyyy
      var profileBio = Aes.encrypt(personalAESKey, personalAESIv, "Someting interesting about me and some junk text "+getRandomId() +".")

      var imgsrc = Array("image1.jpg", "image2.jpg", "image3.jpg")
      var randomid = scala.util.Random.nextInt(imgsrc.length)
      val srcArray = Files.readAllBytes(Paths.get(imgsrc(randomid))) 
      var pictureValue = Aes.encrypt(personalAESKey, personalAESIv, Base64.encodeBase64String(srcArray).toString())
      var pictureTitle = Aes.encrypt(personalAESKey, personalAESIv, "Sample Image Title Text " + getRandomId())
      var pictureDescription = Aes.encrypt(personalAESKey, personalAESIv, "Sample Image Description Text " + getRandomId())
      var pictureCreatedTime = Aes.encrypt(personalAESKey, personalAESIv, Calendar.getInstance().getTime().toString)
      
      var postData = s"""{ "id":"$userId", "profileFirstName":"$profileFirstName", "profileLastName":"$profileLastName", "profileEmail":"$profileEmail", "profileGender":"$profileGender", "profileBirthday":"$profileBirthday", "profileBio":"$profileBio", "pictureTitle":"$pictureTitle", "pictureDescription":"$pictureDescription", "pictureCreatedTime":"$pictureCreatedTime", "pictureValue":"$pictureValue" }"""

      println("Dump postData -> " + postData)
      var req = Post("http://localhost:8080/users", HttpEntity(MediaTypes.`application/json`, postData))
      var postUserFutureResponse: Future[HttpResponse] = pipeline(req)
      postUserFutureResponse.onComplete { response => println(response.get.entity.asString) }
    }
    
    // Authenticate client actor and save the session cookie
    def auth = {
      println("Assigning cookie to actor id:"+self.path.name)
      var activityActor = system.actorOf(Props[ActivityActor], name = "activityActor" + getRandomId())
      var futureAuth = Patterns.ask(activityActor, Auth(self.path.name), Timeout(Duration.create(5, "seconds")))
      var responseAuth = Await.result(futureAuth, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
      context.stop(activityActor)
      
      // extract the Cookie
      httpCookie = responseAuth.headers.collect { case spray.http.HttpHeaders.`Set-Cookie`(hc) => hc }
      
      // add cookie to client pipeline header
      pipeline = addHeader(spray.http.HttpHeaders.Cookie(httpCookie)) ~> sendReceive
      println("Added cookie -> " + httpCookie + " to pipeline header!")
    }
    
    def encryptUserPost(d: UserPost, key: SecretKey, iv: Array[Byte]) = {
      var uPost = UserPost(d.id, d. userId, d.title, d.description, d.createdTime)
      if(!uPost.title.isEmpty) { uPost.title = Some(Aes.encrypt(key, iv, uPost.title.get)) }
      if(!uPost.description.isEmpty) { uPost.description = Some(Aes.encrypt(key, iv, uPost.description.get)) }
      if(!uPost.createdTime.isEmpty) { uPost.createdTime = Some(Aes.encrypt(key, iv, uPost.createdTime.get)) }
      
      uPost      
    }
    
    def decryptUserPost(d: UserPost, key: SecretKey, iv: Array[Byte]) = {
      var uPost = UserPost(d.id, d. userId, d.title, d.description, d.createdTime)
      if(!uPost.title.isEmpty) { uPost.title = Some(Aes.decrypt(key, iv, uPost.title.get)) }
      if(!uPost.description.isEmpty) { uPost.description = Some(Aes.decrypt(key, iv, uPost.description.get)) }
      if(!uPost.createdTime.isEmpty) { uPost.createdTime = Some(Aes.decrypt(key, iv, uPost.createdTime.get)) }
      
      uPost
    }
    
    def encryptUserPostList(d: UserPostList, key: SecretKey, iv: Array[Byte]) = {
      var userPostList = UserPostList(Nil)
      d.userPosts.foreach {
        userPost =>
          var uPost = encryptUserPost(userPost, key, iv)
          userPostList.userPosts = uPost :: userPostList.userPosts
      }
      
      userPostList
    }    
    
    def decryptUserPostList(d: UserPostList, key: SecretKey, iv: Array[Byte]) = {
      var userPostList = UserPostList(Nil)
      d.userPosts.foreach {
        userPost =>
          var uPost = decryptUserPost(userPost, key, iv)
          userPostList.userPosts = uPost :: userPostList.userPosts
      }
      
      userPostList
    }
    
    def encryptPagePost(d: PagePost, key: SecretKey, iv: Array[Byte]) = {
      var pPost = PagePost(d.id, d.pageId, d.creatorId, d.title, d.description, d.createdTime)
      if(!pPost.title.isEmpty) { pPost.title = Some(Aes.encrypt(key, iv, pPost.title.get)) }
      if(!pPost.description.isEmpty) { pPost.description = Some(Aes.encrypt(key, iv, pPost.description.get)) }
      if(!pPost.createdTime.isEmpty) { pPost.createdTime = Some(Aes.encrypt(key, iv, pPost.createdTime.get)) }
      
      pPost      
    }
    
    def decryptPagePost(d: PagePost, key: SecretKey, iv: Array[Byte]) = {
      var pPost = PagePost(d.id, d.pageId, d.creatorId, d.title, d.description, d.createdTime)
      if(!pPost.title.isEmpty) { pPost.title = Some(Aes.decrypt(key, iv, pPost.title.get)) }
      if(!pPost.description.isEmpty) { pPost.description = Some(Aes.decrypt(key, iv, pPost.description.get)) }
      if(!pPost.createdTime.isEmpty) { pPost.createdTime = Some(Aes.decrypt(key, iv, pPost.createdTime.get)) }
      
      pPost
    }
    
    def encryptPage(d: Page, key: SecretKey, iv: Array[Byte]) = {
      var page = Page(d.id, d.userId, Nil)
      d.pagePosts.foreach {
        pagePost =>
          var pPost = encryptPagePost(pagePost, key, iv)
          page.pagePosts = pPost :: page.pagePosts
      }
      
      page
    }    
    
    def decryptPage(d: Page, key: SecretKey, iv: Array[Byte]) = {
      var page = Page(d.id, d.userId, Nil)
      d.pagePosts.foreach {
        pagePost =>
          var pPost = decryptPagePost(pagePost, key, iv)
          page.pagePosts = pPost :: page.pagePosts
      }
      
      page
    } 

    def encryptProfile(d: Profile, key: SecretKey, iv: Array[Byte]) = {
      var profile = Profile(d.id, d.userId, d.firstName, d.lastName, d.email, d.gender, d.birthday, d.bio)
      if(!profile.email.isEmpty) { profile.email = Some(Aes.encrypt(key, iv, profile.email.get)) }
      if(!profile.gender.isEmpty) { profile.gender = Some(Aes.encrypt(key, iv, profile.gender.get)) }
      if(!profile.birthday.isEmpty) { profile.birthday = Some(Aes.encrypt(key, iv, profile.birthday.get)) }
      if(!profile.bio.isEmpty) { profile.bio = Some(Aes.encrypt(key, iv, profile.bio.get)) }
      
      profile      
    }
    
    def decryptProfile(d: Profile, key: SecretKey, iv: Array[Byte]) = {
      var profile = Profile(d.id, d.userId, d.firstName, d.lastName, d.email, d.gender, d.birthday, d.bio)
      if(!profile.email.isEmpty) { profile.email = Some(Aes.decrypt(key, iv, profile.email.get)) }
      if(!profile.gender.isEmpty) { profile.gender = Some(Aes.decrypt(key, iv, profile.gender.get)) }
      if(!profile.birthday.isEmpty) { profile.birthday = Some(Aes.decrypt(key, iv, profile.birthday.get)) }
      if(!profile.bio.isEmpty) { profile.bio = Some(Aes.decrypt(key, iv, profile.bio.get)) }
      
      profile      
    }    
 
    def encryptPicture(d: Picture, key: SecretKey, iv: Array[Byte]) = {
      var picture = Picture(d.id, d. userId, d.value, d.title, d.description, d.createdTime)
      if(!picture.value.isEmpty) { picture.value = Aes.encrypt(key, iv, picture.value) }
      if(!picture.title.isEmpty) { picture.title = Some(Aes.encrypt(key, iv, picture.title.get)) }
      if(!picture.description.isEmpty) { picture.description = Some(Aes.encrypt(key, iv, picture.description.get)) }
      if(!picture.createdTime.isEmpty) { picture.createdTime = Some(Aes.encrypt(key, iv, picture.createdTime.get)) }
      
      picture
    }
    
    def decryptPicture(d: Picture, key: SecretKey, iv: Array[Byte]) = {
      var picture = Picture(d.id, d. userId, d.value, d.title, d.description, d.createdTime)
      if(!picture.value.isEmpty) { picture.value = Aes.decrypt(key, iv, picture.value) }
      if(!picture.title.isEmpty) { picture.title = Some(Aes.decrypt(key, iv, picture.title.get)) }
      if(!picture.description.isEmpty) { picture.description = Some(Aes.decrypt(key, iv, picture.description.get)) }
      if(!picture.createdTime.isEmpty) { picture.createdTime = Some(Aes.decrypt(key, iv, picture.createdTime.get)) }
      
      picture
    }
    
    def encryptPictureList(d: PictureList, key: SecretKey, iv: Array[Byte]) = {
      var pictureList = PictureList(Nil)
      d.pictures.foreach {
        picture =>
          var pic = encryptPicture(picture, key, iv)
          pictureList.pictures = pic :: pictureList.pictures
      }
      
      pictureList
    }    
    
    def decryptPictureList(d: PictureList, key: SecretKey, iv: Array[Byte]) = {
      var pictureList = PictureList(Nil)
      d.pictures.foreach {
        picture =>
          var pic = decryptPicture(picture, key, iv)
          pictureList.pictures = pic :: pictureList.pictures
      }
      
      pictureList
    }
    
    def encryptAlbum(d: Album, key: SecretKey, iv: Array[Byte]) = {
      var album = Album(d.id, d.userId, d.title, d.description, d.createdTime, Nil)
      d.pictures.foreach {
        picture =>
          var pic = encryptPicture(picture, key, iv)
          album.pictures = pic :: album.pictures
      }
      
      album
    }    
    
    def decryptAlbum(d: Album, key: SecretKey, iv: Array[Byte]) = {
      var album = Album(d.id, d.userId, d.title, d.description, d.createdTime, Nil)
      d.pictures.foreach {
        picture =>
          var pic = decryptPicture(picture, key, iv)
          album.pictures = pic :: album.pictures
      }
      
      album
    }      
    
    def simulator = {
      println("Actor: "+self.path.name+" has been hooked with the SIMULATOR!")
      
      // POST post
      var cancellableCreatePost = system.scheduler.schedule(Duration.create(5, TimeUnit.SECONDS), Duration.create(CREATE_POST_FREQ, TimeUnit.SECONDS)) {
        try {
        println("Actor:"+self.path.name+" is creating a new Post.")
        var activityActor = system.actorOf(Props[ActivityActor], name = "activityActor" + getRandomId())
        
        // create new post at the server
        var futureCreatePost = Patterns.ask(activityActor, CreatePost(self.path.name, httpCookie, personalAESKey, personalAESIv), Timeout(Duration.create(5, "seconds")))
        var responseCreatePost = Await.result(futureCreatePost, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
        var userPostResponse = responseCreatePost.entity.data.asString // response body
      
        println("userPostResponse -> " + userPostResponse)
      
        if(!(userPostResponse contains "error") && !(userPostResponse contains "ERROR")) { // post action should not be an error                  
          // get friends to notify about the new post
          //println("Fetching friends of User:"+self.path.name)
          var futureGetFriends = Patterns.ask(activityActor, GetFriends(self.path.name, httpCookie), Timeout(Duration.create(5, "seconds")))
          var responseGetFriends = Await.result(futureGetFriends, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
          var jsonFriendList = responseGetFriends.entity.data.asString.parseJson
          var friendList = jsonFriendList.convertTo[FriendList]
          //println(friendList.toJson.prettyPrint)
                    
          var friends = friendList.friends
          if(!friends.isEmpty) {

            // convert json response to userPost
            var userPostJson = userPostResponse.parseJson
            var userPostObj  = userPostJson.convertTo[UserPost]        
            //println("userPostObj -> " + userPostObj.toJson.prettyPrint)
            
            // decrypt post data from server using personal AES credentials
            var uPostPlainText = decryptUserPost(userPostObj, personalAESKey, personalAESIv)
            //println("uPostPlainText -> "+uPostPlainText)
            
            // Encrypting userPost before notifying friends
            var newAesKey    = Aes.genRandKey()
            var newAesIv     = Aes.genRandIv()
            var newAesKeyStr = Base64.encodeBase64String(newAesKey.getEncoded)
            var newAesIvStr  = Base64.encodeBase64String(newAesIv)
            
            var uPostEncrypted = encryptUserPost(uPostPlainText, newAesKey, newAesIv) // encrypt post
            
            println("\nNotifying all friends of client:"+self.path.name+" that a new post was created ....\n")
            friends.foreach { 
              friend =>
                var friendUserId = friend.id
                var friendActorInfo = actorInfos(friendUserId)
                
                //println("Encrypting aesKey and aesIv using RSA public key of friend ...")
                var newAesKeyEncrypted = Rsa.encrypt(friendActorInfo.pubKey, newAesKeyStr)         
                var newAesIvEncrypted = Rsa.encrypt(friendActorInfo.pubKey, newAesIvStr)
            
                // securely send userPostEncrypted, aesKeyEncrypted, aesIvEncrypted to Friends
                friendActorInfo.ref ! GetPost(uPostEncrypted, newAesKeyEncrypted, newAesIvEncrypted)
            }          
          } else {
            println("NO FRIENDS FOUND!")
          }
        } else {
          //println("ERROR FOUND INSIDE POST-ID") 
        }
      
        context.stop(activityActor) // stop activity actor
        
        } catch {
          case t: Throwable => {
            //println("ERROR inside GET post executed by Actor:" + self.path.name)
          }
        }
        
      } // cancellable
      
      
      // GET post
      var cancellableGetPost = system.scheduler.schedule(Duration.create(10, TimeUnit.SECONDS), Duration.create(GET_POSTS_FREQ, TimeUnit.SECONDS)) {
        try {
        println("Actor:"+self.path.name+" is fetching a Random friend's Posts:")
        var activityActor = system.actorOf(Props[ActivityActor], name = "activityActor" + getRandomId())
        
        // get random friend
        var futureRandomFriend = Patterns.ask(activityActor, GetRandomFriend(self.path.name, httpCookie), Timeout(Duration.create(5, "seconds")))
        var responseRandomFriend = Await.result(futureRandomFriend, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
        var randomFriendUserId = responseRandomFriend.entity.data.asString
        
        //println("RANDOM FRIEND FOUND -> " + randomFriendUserId)
        
        if((randomFriendUserId != null) && (randomFriendUserId != self.path.name) && randomFriendUserId.length > 3) {
          println("Actor: "+self.path.name+"  is fetching userPosts of Friend: "+randomFriendUserId)
          
          //println("notify the friend actor to fetch data from the server ...")
          var bWait = busyWait.get(randomFriendUserId)
          
          if((!bWait.isEmpty) && (bWait.get == self.path.name)) {
            //println("Dropping Request to avoid deadlock." + self.path.name +" -> " + randomFriendUserId)
            
          } else {
            busyWait(self.path.name) = randomFriendUserId
            var friendActor = actorInfos(randomFriendUserId).ref
            var futureGetPosts = Patterns.ask(friendActor, GetPostsFromServer(randomFriendUserId, publicKey), Timeout(Duration.create(5, "seconds")))
            var userPostListEncrypt = Await.result(futureGetPosts, Duration.create(5, "seconds")).asInstanceOf[UserPostListEncrypt]
            busyWait(self.path.name) = null
  
            //println("Received userPosts from the owner! Decrypting it ...")
            var aesCreds = getAesCredsFromRsaCiphers(userPostListEncrypt.aesKey, userPostListEncrypt.aesIv)
            var uPost = decryptUserPostList(userPostListEncrypt.userPostList, aesCreds._1, aesCreds._2)
            println("User:"+self.path.name+" decrypted Post of Friend:"+randomFriendUserId+" -> " + uPost)
          }
          
        }
        
        context.stop(activityActor) // stop activity actor
        
        } catch {
          case t: Throwable => {
            //println("ERROR inside GET post executed by Actor:" + self.path.name)
          }
        }
      } // cancellable

      
      // POST pagePost
      var cancellableCreatePagePost = system.scheduler.schedule(Duration.create(15, TimeUnit.SECONDS), Duration.create(CREATE_PAGE_POSTS_FREQ, TimeUnit.SECONDS)) {
        try {
        println("Actor:"+self.path.name+" is Posting on a friend's Page.")
        
        var activityActor = system.actorOf(Props[ActivityActor], name = "activityActor" + getRandomId())
      
        // get a random friend of the current actor to make a page post
        //println("Fetching random friend of User:"+self.path.name)
        var futureRandomFriend = Patterns.ask(activityActor, GetRandomFriend(self.path.name, httpCookie), Timeout(Duration.create(5, "seconds")))
        var responseRandomFriend = Await.result(futureRandomFriend, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
        var randomFriendUserId = responseRandomFriend.entity.data.asString
        
        if((randomFriendUserId != null) && randomFriendUserId.length > 3) {
          //println("RANDOM FRIEND FOUND for actor:" +self.path.name+ " --> " + randomFriendUserId)
          var randomFriendActorInfo = actorInfos(randomFriendUserId)
          
          println("Actor: "+self.path.name+" is creating a Page-Post for Friend: "+randomFriendUserId)
          
          // create new post on this friend's page on the server
          var futureCreatePagePost = Patterns.ask(activityActor, CreatePagePost(randomFriendUserId, httpCookie, personalAESKey, personalAESIv), Timeout(Duration.create(5, "seconds")))
          var responseCreatePagePost = Await.result(futureCreatePagePost, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
          var pagePostResponse = responseCreatePagePost.entity.data.asString // response body
          println("pagePostResponse -> " + pagePostResponse)
          
          if(!(pagePostResponse contains "error") && !(pagePostResponse contains "ERROR")) {
            // get friends of this randomUserId to notify about the new post
            //println("Fetching friends of User:"+randomFriendUserId)
            var futureGetFriends = Patterns.ask(activityActor, GetFriends(randomFriendUserId, httpCookie), Timeout(Duration.create(5, "seconds")))
            var responseGetFriends = Await.result(futureGetFriends, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
            var jsonFriendList = responseGetFriends.entity.data.asString.parseJson
            var friendList = jsonFriendList.convertTo[FriendList]
            
            var friends = friendList.friends
            if(!friends.isEmpty) {                        
              // convert json response to pagePost
              var pagePostJson = pagePostResponse.parseJson
              var pagePostObj = pagePostJson.convertTo[PagePost]
              //println("pagePostObj -> " + pagePostObj.toJson.prettyPrint)
              
              // decrypt post data from server using personal AES credentials
              var pagePostPlainText = decryptPagePost(pagePostObj, personalAESKey, personalAESIv)
              //println("pagePostPlainText -> "+ pagePostPlainText)

              // Encrypting userPost before notifying friends
              var newAesKey    = Aes.genRandKey()
              var newAesIv     = Aes.genRandIv()
              var newAesKeyStr = Base64.encodeBase64String(newAesKey.getEncoded)
              var newAesIvStr  = Base64.encodeBase64String(newAesIv)
            
              var pagePostEncrypted = encryptPagePost(pagePostPlainText, newAesKey, newAesIv) // encrypt post
              
              println("\nNotifying all friends of client:"+self.path.name+" that a new Page-Post was created ....\n")

              friends.foreach { 
                friend =>
                  var friendUserId = friend.id
                  var friendActorInfo = actorInfos(friendUserId)
                  
                  //println("Encrypting aesKey and aesIv using RSA public key of friend ...")
                  var newAesKeyEncrypted = Rsa.encrypt(friendActorInfo.pubKey, newAesKeyStr)         
                  var newAesIvEncrypted = Rsa.encrypt(friendActorInfo.pubKey, newAesIvStr)
              
                  // securely send userPostEncrypted, aesKeyEncrypted, aesIvEncrypted to Friends
                  friendActorInfo.ref ! GetPagePost(pagePostEncrypted, newAesKeyEncrypted, newAesIvEncrypted)

              }
              
              // notify the randFriendActor to see its page as well.
              // randomFriendActorInfo.ref ! GetPagePost(pagePostEncrypted, aes-key-encrypt, aes-iv-encrypt)
            } else {
              println("No friends found!")
            }
          } else {
            //println("ERROR found inside page-post-id!")
          }
        } else {
            println("No Random friend found for user:"+self.path.name)
        }
        
        context.stop(activityActor)
        
        } catch {
          case t: Throwable => {
            //println("ERROR inside Post PagePost executed by Actor:" + self.path.name)
          }
        }
        
      } // cancellable
            
      // PUT profile
      var cancellableUpdateProfile = system.scheduler.schedule(Duration.create(10, TimeUnit.SECONDS), Duration.create(PUT_PROFILE_FREQ, TimeUnit.SECONDS)) {
        try {
        println("Actor:"+self.path.name+" is Updating Profile.")
        
        var activityActor = system.actorOf(Props[ActivityActor], name = "activityActor" + getRandomId())
        
        // update current actor's profile at server
        var futureUpdateProfile = Patterns.ask(activityActor, PutProfile(self.path.name, httpCookie, personalAESKey, personalAESIv), Timeout(Duration.create(5, "seconds")))
        var responseUpdateProfile = Await.result(futureUpdateProfile, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
        var profileResponse = responseUpdateProfile.entity.data.asString // response body
        println("profileResponse -> " + profileResponse)
        
        if(!(profileResponse contains "error") && !(profileResponse contains "ERROR")) { // put action should not be an error
          //println("Fetching friends of User:"+self.path.name) // get friends to notify about the profile update
          var futureGetFriends = Patterns.ask(activityActor, GetFriends(self.path.name, httpCookie), Timeout(Duration.create(5, "seconds")))
          var responseGetFriends = Await.result(futureGetFriends, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
          var jsonFriendList = responseGetFriends.entity.data.asString.parseJson
          var friendList = jsonFriendList.convertTo[FriendList]
          //println(friendList.toJson.prettyPrint)
          
          var friends = friendList.friends
          if(!friends.isEmpty) {
            var profileObj = (profileResponse.parseJson).convertTo[Profile]
            //println("profileObj -> " + profileObj.toJson.prettyPrint)
            
            // decrypt post data from server using personal AES credentials
            var profilePlainText = decryptProfile(profileObj, personalAESKey, personalAESIv)
            //println("profilePlainText -> "+profilePlainText)
            
            // Encrypting userPost before notifying friends
            var newAesKey    = Aes.genRandKey()
            var newAesIv     = Aes.genRandIv()
            var newAesKeyStr = Base64.encodeBase64String(newAesKey.getEncoded)
            var newAesIvStr  = Base64.encodeBase64String(newAesIv)
            
            var profileEncrypted = encryptProfile(profilePlainText, newAesKey, newAesIv) // encrypt post

            println("\nNotifying all friends of client:"+self.path.name+" about the Profile update ....\n")

            friends.foreach { 
              friend =>
                var friendUserId = friend.id
                var friendActorInfo = actorInfos(friendUserId)
                
                //println("Encrypting aesKey and aesIv using RSA public key of friend ...")
                var newAesKeyEncrypted = Rsa.encrypt(friendActorInfo.pubKey, newAesKeyStr)         
                var newAesIvEncrypted = Rsa.encrypt(friendActorInfo.pubKey, newAesIvStr)
            
                // securely send userPostEncrypted, aesKeyEncrypted, aesIvEncrypted to Friends
                friendActorInfo.ref ! GetProfile(profileEncrypted, newAesKeyEncrypted, newAesIvEncrypted)
            }          
          } else {
            println("No friends found for User:"+self.path.name)
          }
        } else {
          //println("ERROR found inside PUT Profile Response!")
        }
        
        context.stop(activityActor) // stop activity actor
        
        } catch {
          case t: Throwable => {
            //println("ERROR inside Put Profile executed by Actor:" + self.path.name)
          }
        }
        
      } // cancellable
      
      
      // GET profile
      var cancellableGetProfile = system.scheduler.schedule(Duration.create(5, TimeUnit.SECONDS), Duration.create(GET_PROFILE_FREQ, TimeUnit.SECONDS)) {
        try {
        println("Actor:"+self.path.name+" is inside Requesting a Random user's profile.")
        var activityActor = system.actorOf(Props[ActivityActor], name = "activityActor" + getRandomId())
        
        // get random user
        var randomUserId = getRandUser()
        //println("RANDOM User FOUND -> " + randomUserId)
        
        if(randomUserId != self.path.name) {
          println("Actor: "+self.path.name+" called get profile of User: "+randomUserId)
          
          var bWait = busyWait.get(randomUserId)
          
          if((!bWait.isEmpty) && (bWait.get == self.path.name)) {
            //println("Dropping Request to avoid deadlock." + self.path.name +" -> " + randomUserId)
            
          } else {
            busyWait(self.path.name) = randomUserId
            var userActor = actorInfos(randomUserId).ref
            var futureGetProfile = Patterns.ask(userActor, GetProfileFromServer(randomUserId, publicKey), Timeout(Duration.create(5, "seconds")))
            var profileEncrypt = Await.result(futureGetProfile, Duration.create(5, "seconds")).asInstanceOf[ProfileEncrypt]
            busyWait(self.path.name) = null
  
            //println("Received profile from the owner! Decrypting it ...")
            var aesCreds = getAesCredsFromRsaCiphers(profileEncrypt.aesKey, profileEncrypt.aesIv)
            var profile = decryptProfile(profileEncrypt.profile, aesCreds._1, aesCreds._2)
            println("User: "+self.path.name+" decrypted Profile of the User: "+randomUserId+" -> " + profile)
          }
        }
        
        context.stop(activityActor) // stop activity actor
        
        } catch {
          case t: Throwable => {
            //println("ERROR inside Get Profile executed by Actor:" + self.path.name)
          }
        }
        
      } // cancellable
      

      // POST friend
      var cancellableAddFriend = system.scheduler.schedule(Duration.create(10, TimeUnit.SECONDS), Duration.create(POST_FRIEND_FREQ, TimeUnit.SECONDS)) {
        try {
        println("Actor:"+self.path.name+" is adding a new Friend.")
        var activityActor = system.actorOf(Props[ActivityActor], name = "activityActor" + getRandomId())
        
        // get a random user (non friend) to add as a friend
        //println("Fetching random non friend of User:"+self.path.name)
        var futureRandomNonFriend = Patterns.ask(activityActor, GetRandomNonFriend(self.path.name, httpCookie), Timeout(Duration.create(5, "seconds")))
        var responseRandomNonFriend = Await.result(futureRandomNonFriend, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
        var randomNonFriendUserId = responseRandomNonFriend.entity.data.asString
        
        //println("RANDOM Non Friend FOUND -> " + randomNonFriendUserId)
        
        if((randomNonFriendUserId != null) && (randomNonFriendUserId.length > 3)) { // non friend found
          var randomNonFriendActor = actorInfos(randomNonFriendUserId).ref     
                  
          // add randomNonFriendUserId as friend for the current user on the server
          var futureAddFriend = Patterns.ask(activityActor, AddFriend(randomNonFriendUserId, self.path.name, httpCookie), Timeout(Duration.create(5, "seconds")))
          var responseAddFriend = Await.result(futureAddFriend, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
          var res = responseAddFriend.entity.data.asString // response body
          
          if(!(res contains "ERROR") && !(res contains "error")) {
            println("User:" + self.path.name + " added a friend:"+randomNonFriendUserId)
            
            //println("Fetching friends of User:"+self.path.name)
            var futureGetFriends = Patterns.ask(activityActor, GetFriends(self.path.name, httpCookie), Timeout(Duration.create(5, "seconds")))
            var responseGetFriends = Await.result(futureGetFriends, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
            var jsonFriendList = responseGetFriends.entity.data.asString.parseJson
            var friendList = jsonFriendList.convertTo[FriendList]
            println(friendList.toJson.prettyPrint)
            
            var friends = friendList.friends
            if(!friends.isEmpty) {
              println("\nNotifying all friends of client:"+self.path.name+" that a Friend was added ....\n")

              friends.foreach { 
                friend =>
                  var friendActor = actorInfos(friend.id).ref
                  friendActor ! GetFriend(randomNonFriendUserId, self.path.name)
              }          
            } else {
              println("No friends found!")
            }
          } else {
            //println("ERROR found inside POST Friends Response!")
          }
        } else {
          println("No Non-Friend found for user: " + self.path.name)
        }
        
        context.stop(activityActor) // stop activity actor
      
        } catch {
          case t: Throwable => {
            //println("ERROR inside POST friends executed by Actor:" + self.path.name)
          }
        }
        
      } // cancellable

      // GET friends
      var cancellableGetFriends = system.scheduler.schedule(Duration.create(15, TimeUnit.SECONDS), Duration.create(GET_FRIENDS_FREQ, TimeUnit.SECONDS)) {
        try {
        println("Actor:"+self.path.name+" is requesting friends of a Random User.")
        var activityActor = system.actorOf(Props[ActivityActor], name = "activityActor" + getRandomId())
        
        // get random user
        var randomUserId = getRandUser()
        //println("RANDOM User FOUND -> " + randomUserId)
        
        if(randomUserId != self.path.name) {
          println("Actor: "+self.path.name+" called get friends of User: "+randomUserId)
          
          var bWait = busyWait.get(randomUserId)
          if((!bWait.isEmpty) && (bWait.get == self.path.name)) {
            //println("Dropping Request to avoid deadlock." + self.path.name +" -> " + randomUserId)
            
          } else {
            busyWait(self.path.name) = randomUserId        
            var futureGetFriends = Patterns.ask(activityActor, GetFriends(randomUserId, httpCookie), Timeout(Duration.create(5, "seconds")))
            var responseGetFriends = Await.result(futureGetFriends, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
            println("User: "+self.path.name+"Friends of User:"+randomUserId+" -> "+responseGetFriends.entity.data.asString)
            busyWait(self.path.name) = null
          }
        }
        
        context.stop(activityActor) // stop activity actor
        
        } catch {
          case t: Throwable => {
            //println("ERROR inside GET friends executed by Actor:" + self.path.name)
          }
        }                
      } // cancellable 
      
      
      // POST picture
      var cancellablePostPicture = system.scheduler.schedule(Duration.create(15, TimeUnit.SECONDS), Duration.create(POST_PICTURE_FREQ, TimeUnit.SECONDS)) {
        try {
        println("Actor:"+self.path.name+" is posting a new Picture.")
        var activityActor = system.actorOf(Props[ActivityActor], name = "activityActor" + getRandomId())
        
        // create new picture at the server
        var futurePostPicture = Patterns.ask(activityActor, PostPicture(self.path.name, httpCookie, personalAESKey, personalAESIv), Timeout(Duration.create(5, "seconds")))
        var responsePostPicture = Await.result(futurePostPicture, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
      
        var pictureResponse = responsePostPicture.entity.data.asString // response body
        println("pictureResponse -> "  + pictureResponse)
        
        if(!(pictureResponse contains "error") && !(pictureResponse contains "ERROR")) { // post action should not be an error
          // get friends to notify about the new picture
          //println("Fetching friends of User:"+self.path.name)
          var futureGetFriends = Patterns.ask(activityActor, GetFriends(self.path.name, httpCookie), Timeout(Duration.create(5, "seconds")))
          var responseGetFriends = Await.result(futureGetFriends, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
          var jsonFriendList = responseGetFriends.entity.data.asString.parseJson
          var friendList = jsonFriendList.convertTo[FriendList]
          println(friendList.toJson.prettyPrint)
          
          var friends = friendList.friends
          if(!friends.isEmpty) {
                        
            // convert json response to picture object
            var pictureJson = pictureResponse.parseJson
            var pictureObj  = pictureJson.convertTo[Picture]        
            //println("pictureObj -> " + pictureObj.toJson.prettyPrint)
            
            // decrypt picture from server using personal AES credentials
            var picturePlainText = decryptPicture(pictureObj, personalAESKey, personalAESIv)
            //println("picturePlainText -> "+picturePlainText)
            
            // Encrypting picture before notifying friends
            var newAesKey    = Aes.genRandKey()
            var newAesIv     = Aes.genRandIv()
            var newAesKeyStr = Base64.encodeBase64String(newAesKey.getEncoded)
            var newAesIvStr  = Base64.encodeBase64String(newAesIv)
            
            var pictureEncrypted = encryptPicture(picturePlainText, newAesKey, newAesIv) // encrypt picture

            println("\nNotifying all friends of client:"+self.path.name+" that a new picture was posted ....\n")
            
            friends.foreach { 
              friend =>
                var friendUserId = friend.id
                var friendActorInfo = actorInfos(friendUserId)
                
                //println("Encrypting aesKey and aesIv using RSA public key of friend ...")
                var newAesKeyEncrypted = Rsa.encrypt(friendActorInfo.pubKey, newAesKeyStr)         
                var newAesIvEncrypted = Rsa.encrypt(friendActorInfo.pubKey, newAesIvStr)
            
                // securely send userPostEncrypted, aesKeyEncrypted, aesIvEncrypted to Friends
                friendActorInfo.ref ! GetPicture(pictureEncrypted, newAesKeyEncrypted, newAesIvEncrypted)
                
            }          
          } else {
            println("NO FRIENDS FOUND!")
          }
        } else {
          //println("ERROR FOUND INSIDE PICTURE-ID") 
        }
      
        context.stop(activityActor) // stop activity actor
        
        } catch {
          case t: Throwable => {
            //println("ERROR inside POST picture executed by Actor:" + self.path.name)
          }
        }
        
      } // cancellable
      
      // GET pictures
      var cancellableGetPictures = system.scheduler.schedule(Duration.create(10, TimeUnit.SECONDS), Duration.create(GET_PICTURES_FREQ, TimeUnit.SECONDS)) {
        try {
        println("Actor:"+self.path.name+" is fetching the PictureList of a Random Friend.")
        var activityActor = system.actorOf(Props[ActivityActor], name = "activityActor" + getRandomId())
        
        // get random friend
        var futureRandomFriend = Patterns.ask(activityActor, GetRandomFriend(self.path.name, httpCookie), Timeout(Duration.create(5, "seconds")))
        var responseRandomFriend = Await.result(futureRandomFriend, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
        var randomFriendUserId = responseRandomFriend.entity.data.asString
        
        //println("RANDOM FRIEND FOUND -> " + randomFriendUserId)
        
        if((randomFriendUserId != null) && (randomFriendUserId != self.path.name) && randomFriendUserId.length > 3) {
          println("Actor: "+self.path.name+"  is fetching PictureList of Friend: "+randomFriendUserId)
          
          var bWait = busyWait.get(randomFriendUserId)
          
          if((!bWait.isEmpty) && (bWait.get == self.path.name)) {
            //println("Dropping Request to avoid deadlock." + self.path.name +" -> " + randomFriendUserId)
            
          } else {
            busyWait(self.path.name) = randomFriendUserId
            var friendActor = actorInfos(randomFriendUserId).ref
            var futureGetPictures = Patterns.ask(friendActor, GetPicturesFromServer(randomFriendUserId, publicKey), Timeout(Duration.create(5, "seconds")))
            var pictureListEncrypt = Await.result(futureGetPictures, Duration.create(5, "seconds")).asInstanceOf[PictureListEncrypt]
            busyWait(self.path.name) = null
  
            //println("Received picture list from the owner! Decrypting it ...")
            var aesCreds = getAesCredsFromRsaCiphers(pictureListEncrypt.aesKey, pictureListEncrypt.aesIv)
            var pictureList = decryptPictureList(pictureListEncrypt.pictureList, aesCreds._1, aesCreds._2)
            println("User:"+self.path.name+" decrypted PictureList of Friend:"+randomFriendUserId+" -> " + pictureList)
          }
        }
        
        context.stop(activityActor) // stop activity actor
        
        } catch {
          case t: Throwable => {
            //println("ERROR inside GET pictures executed by Actor:" + self.path.name)
          }
        }
      } // cancellable
      
       //POST album picture
      var cancellablePostAlbumPicture = system.scheduler.schedule(Duration.create(15, TimeUnit.SECONDS), Duration.create(POST_ALBUM_PICTURE_FREQ, TimeUnit.SECONDS)) {
        try {
        println("Actor:"+self.path.name+" is posting a new Album Picture.")
        var activityActor = system.actorOf(Props[ActivityActor], name = "activityActor" + getRandomId())
        
        // create new album picture at the server
        var futurePostAlbumPicture = Patterns.ask(activityActor, PostAlbumPicture(self.path.name, httpCookie, personalAESKey, personalAESIv), Timeout(Duration.create(5, "seconds")))
        var responsePostAlbumPicture = Await.result(futurePostAlbumPicture, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
      
        var pictureResponse = responsePostAlbumPicture.entity.data.asString // response body
        println("albumPictureResponse -> " + pictureResponse)
        
        if(!(pictureResponse contains "error") && !(pictureResponse contains "ERROR")) { // post action should not be an error
          var futureGetFriends = Patterns.ask(activityActor, GetFriends(self.path.name, httpCookie), Timeout(Duration.create(5, "seconds")))
          var responseGetFriends = Await.result(futureGetFriends, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
          var jsonFriendList = responseGetFriends.entity.data.asString.parseJson
          var friendList = jsonFriendList.convertTo[FriendList]
          //println(friendList.toJson.prettyPrint)
          
          var friends = friendList.friends
          if(!friends.isEmpty) {
            // convert json response to picture object
            var pictureJson = pictureResponse.parseJson
            var pictureObj  = pictureJson.convertTo[Picture]        
            //println("albumPictureObj -> " + pictureObj.toJson.prettyPrint)
            
            // decrypt picture from server using personal AES credentials
            var picturePlainText = decryptPicture(pictureObj, personalAESKey, personalAESIv)
            //println("albumPicturePlainText -> " + picturePlainText)
            
            // Encrypting picture before notifying friends
            var newAesKey    = Aes.genRandKey()
            var newAesIv     = Aes.genRandIv()
            var newAesKeyStr = Base64.encodeBase64String(newAesKey.getEncoded)
            var newAesIvStr  = Base64.encodeBase64String(newAesIv)
            
            var pictureEncrypted = encryptPicture(picturePlainText, newAesKey, newAesIv) // encrypt picture
            
            println("\nNotifying all friends of client:"+self.path.name+" that a new Album Picture was added ....\n")
            friends.foreach { 
              friend =>
                var friendUserId = friend.id
                var friendActorInfo = actorInfos(friendUserId)
                
                //println("Encrypting aesKey and aesIv using RSA public key of friend ...")
                var newAesKeyEncrypted = Rsa.encrypt(friendActorInfo.pubKey, newAesKeyStr)         
                var newAesIvEncrypted = Rsa.encrypt(friendActorInfo.pubKey, newAesIvStr)
            
                // securely send encrypted data to to friends
                friendActorInfo.ref ! GetPicture(pictureEncrypted, newAesKeyEncrypted, newAesIvEncrypted)
                
            }          
          } else {
            println("No friends found!")
          }
        } else {
          //println("ERROR FOUND INSIDE ALBUM-PICTURE-ID") 
        }
      
        context.stop(activityActor) // stop activity actor
        
        } catch {
          case t: Throwable => {
            //println("ERROR inside POST album picture executed by Actor:" + self.path.name)
          }
        }
        
      } // cancellable
      
      // GET album
      var cancellableGetAlbum = system.scheduler.schedule(Duration.create(10, TimeUnit.SECONDS), Duration.create(GET_ALBUM_FREQ, TimeUnit.SECONDS)) {
        try {
        println("Actor:"+self.path.name+" is fetching a Random friend's Album.")
        var activityActor = system.actorOf(Props[ActivityActor], name = "activityActor" + getRandomId())
        
        // get random friend
        var futureRandomFriend = Patterns.ask(activityActor, GetRandomFriend(self.path.name, httpCookie), Timeout(Duration.create(5, "seconds")))
        var responseRandomFriend = Await.result(futureRandomFriend, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
        var randomFriendUserId = responseRandomFriend.entity.data.asString
        
        //println("RANDOM FRIEND FOUND -> " + randomFriendUserId)
        
        if((randomFriendUserId != null) && (randomFriendUserId != self.path.name) && randomFriendUserId.length > 3) {          
          println("Actor: "+self.path.name+"  is fetching Album of Friend: "+randomFriendUserId)

          var bWait = busyWait.get(randomFriendUserId)
          
          if((!bWait.isEmpty) && (bWait.get == self.path.name)) {
            //println("Dropping Request to avoid deadlock." + self.path.name +" -> " + randomFriendUserId)
            
          } else {
            busyWait(self.path.name) = randomFriendUserId
            var friendActor = actorInfos(randomFriendUserId).ref
            var futureGetAlbum = Patterns.ask(friendActor, GetAlbumFromServer(randomFriendUserId, publicKey), Timeout(Duration.create(5, "seconds")))
            var albumEncrypt = Await.result(futureGetAlbum, Duration.create(5, "seconds")).asInstanceOf[AlbumEncrypt]
            busyWait(self.path.name) = null
  
            //println("Received Album from the owner! Decrypting it ...")
            var aesCreds = getAesCredsFromRsaCiphers(albumEncrypt.aesKey, albumEncrypt.aesIv)
            var album = decryptAlbum(albumEncrypt.album, aesCreds._1, aesCreds._2)
            println("User:"+self.path.name+" decrypted Album of Friend:"+randomFriendUserId+" -> " + album)

          }          
        } else {
          println("No random friend found!")
        }
        
        context.stop(activityActor) // stop activity actor
        
        } catch {
          case t: Throwable => {
            //println("ERROR inside GET Album executed by Actor:" + self.path.name)
          }
        }
      } // cancellable

      
    } // def simulator

    def getAesCredsFromRsaCiphers(aesKeyEncrypted: String, aesIvEncrypted: String) = {
      // decrypt creds using private key of this actor
      var aesKeyStr = Rsa.decrypt(aesKeyEncrypted, privateKey) 
      var aesIvStr  = Rsa.decrypt(aesIvEncrypted, privateKey)
              
      // convert string to format compatible with encrypt/decrypt methods
      var aesKeyByteArray = Base64.decodeBase64(aesKeyStr.getBytes)
      var aesKeyOriginal  = new javax.crypto.spec.SecretKeySpec(aesKeyByteArray, "AES")
      var aesIvOriginal   = Base64.decodeBase64(aesIvStr)
      //println("aesKeyByteArray -> "+Base64.encodeBase64String(aesKeyOriginal.getEncoded) + ", aesIv -> " + Base64.encodeBase64String(aesIvOriginal))
      
      (aesKeyOriginal, aesIvOriginal)
    }
    
    def receive = {
      case InitUser => 
        sender ! initUser
        
      case AuthUser =>
        sender ! auth
        
      case InitFriend(id) => 
        var postData = s"""{ "id":"$id" }"""
        pipeline(Post("http://localhost:8080/users/"+self.path.name+"/friends", HttpEntity(MediaTypes.`application/json`, postData))).pipeTo(sender)   
        
      case Simulator => simulator
      
      case GetPost(uPostEncrypted, aesKeyEncrypted, aesIvEncrypted) =>
        var aesCreds = getAesCredsFromRsaCiphers(aesKeyEncrypted, aesIvEncrypted)
        var uPost = decryptUserPost(uPostEncrypted, aesCreds._1, aesCreds._2)
        println("User: "+self.path.name+" decrypted the User Post -> " + uPost)
        
      case GetPostsFromServer(userId, senderPubKey) =>
        println("User: " + userId + " - Fetching its UserPosts from the server and securely (AES-128 + RSA-1024) sending it to the requesting Actor.")
        var activityActor = system.actorOf(Props[ActivityActor], name = "activityActor" + getRandomId()) // delegate
        var futureGetPosts = Patterns.ask(activityActor, GetPosts(userId, httpCookie), Timeout(Duration.create(5, "seconds")))
        var responseGetPosts = Await.result(futureGetPosts, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
        context.stop(activityActor)
        
        var jsonUserPostList = responseGetPosts.entity.data.asString.parseJson
        var userPostList = jsonUserPostList.convertTo[UserPostList]
        //println("userPostList from server -> "  + userPostList)
        
        var uPostListPlainText = decryptUserPostList(userPostList, personalAESKey, personalAESIv)
        //println("uPostListPlainText by owner -> " + uPostListPlainText)
        
        // Encrypting userPostList
        var newAesKey    = Aes.genRandKey()
        var newAesIv     = Aes.genRandIv()
        var newAesKeyStr = Base64.encodeBase64String(newAesKey.getEncoded)
        var newAesIvStr  = Base64.encodeBase64String(newAesIv)
        
        var uPostListEncrypted = encryptUserPostList(uPostListPlainText, newAesKey, newAesIv) // encrypt post
        
        //println("Encrypting aesKey and aesIv using RSA public key of sender ...")
        var newAesKeyEncrypted = Rsa.encrypt(senderPubKey, newAesKeyStr)         
        var newAesIvEncrypted = Rsa.encrypt(senderPubKey, newAesIvStr)
    
        //println("Securely sending data to the user who requested it.")
        sender ! UserPostListEncrypt(uPostListEncrypted, newAesKeyEncrypted, newAesIvEncrypted)     
             
     
      case GetPagePost(pagePostEncrypted, aesKeyEncrypted, aesIvEncrypted) =>
        var aesCreds = getAesCredsFromRsaCiphers(aesKeyEncrypted, aesIvEncrypted)
        var pPost = decryptPagePost(pagePostEncrypted, aesCreds._1, aesCreds._2)
        println("User: "+self.path.name+" decrypted the Page Post -> " + pPost)       
        
      case GetProfile(profileEncrypted, aesKeyEncrypted, aesIvEncrypted) =>
        var aesCreds = getAesCredsFromRsaCiphers(aesKeyEncrypted, aesIvEncrypted)
        var profile = decryptProfile(profileEncrypted, aesCreds._1, aesCreds._2)
        println("User: "+self.path.name+" decrypted the Profile -> " + profile)       
        
      case GetProfileFromServer(userId, senderPubKey) =>
        println("User: " + userId + " - Fetching its Profile from the server and securely (AES-128 + RSA-1024) sending it to the requesting Actor.")
        var activityActor = system.actorOf(Props[ActivityActor], name = "activityActor" + getRandomId()) // delegate
        var futureGetProfile = Patterns.ask(activityActor, GetProfile2(userId, httpCookie), Timeout(Duration.create(5, "seconds")))
        var responseGetProfile = Await.result(futureGetProfile, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
        context.stop(activityActor)
        
        var jsonProfile = responseGetProfile.entity.data.asString.parseJson
        var profile = jsonProfile.convertTo[Profile]
        //println("profile from server -> "  + profile)
        
        var profilePlainText = decryptProfile(profile, personalAESKey, personalAESIv)
        //println("profilePlainText by owner -> " + profilePlainText)
        
        // Encrypting profile
        var newAesKey    = Aes.genRandKey()
        var newAesIv     = Aes.genRandIv()
        var newAesKeyStr = Base64.encodeBase64String(newAesKey.getEncoded)
        var newAesIvStr  = Base64.encodeBase64String(newAesIv)
        
        var profileEncrypted = encryptProfile(profilePlainText, newAesKey, newAesIv) // encrypt post
        
        //println("Encrypting aesKey and aesIv using RSA public key of sender ...")
        var newAesKeyEncrypted = Rsa.encrypt(senderPubKey, newAesKeyStr)         
        var newAesIvEncrypted = Rsa.encrypt(senderPubKey, newAesIvStr)
    
        //println("securely send data back to the user who requested it ...")
        sender ! ProfileEncrypt(profileEncrypted, newAesKeyEncrypted, newAesIvEncrypted)           
        
      case GetFriend(friendUserId, userId) =>
        //println("Actor: "+self.path.name + " is fetching Friend: "+friendUserId)
        var getFriendFutureResponse: Future[HttpResponse] = pipeline(Get("http://localhost:8080/users/"+userId+"/friends/"+friendUserId))          
        getFriendFutureResponse.onComplete { response => println(response.get.entity.data.asString.parseJson.prettyPrint) }
      
      case GetPicture(pictureEncrypted, aesKeyEncrypted, aesIvEncrypted) =>
        var aesCreds = getAesCredsFromRsaCiphers(aesKeyEncrypted, aesIvEncrypted)
        var picture = decryptPicture(pictureEncrypted, aesCreds._1, aesCreds._2)
        println("User: "+self.path.name+" decrypted the Picture -> " + picture)
        
      case GetPicturesFromServer(userId, senderPubKey) =>
        println("User: " + userId + " - Fetching Pictures from the server and securely (AES-128 + RSA-1024) sending it to the requesting Actor.")
        var activityActor = system.actorOf(Props[ActivityActor], name = "activityActor" + getRandomId()) // delegate
        var futureGetPictures = Patterns.ask(activityActor, GetPictures(userId, httpCookie), Timeout(Duration.create(5, "seconds")))
        var responseGetPictures = Await.result(futureGetPictures, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
        context.stop(activityActor)
        
        var jsonPictureList = responseGetPictures.entity.data.asString.parseJson
        var pictureList = jsonPictureList.convertTo[PictureList]
        //println("pictureList from server -> "  + pictureList)
        
        var pictureListPlainText = decryptPictureList(pictureList, personalAESKey, personalAESIv)
        //println("pictureListPlainText by owner -> " + pictureListPlainText)
        
        // Encrypting pictureList
        var newAesKey    = Aes.genRandKey()
        var newAesIv     = Aes.genRandIv()
        var newAesKeyStr = Base64.encodeBase64String(newAesKey.getEncoded)
        var newAesIvStr  = Base64.encodeBase64String(newAesIv)
        
        var pictureListEncrypted = encryptPictureList(pictureListPlainText, newAesKey, newAesIv) // encrypt picture
        
        //println("Encrypting aesKey and aesIv using RSA public key of sender ...")
        var newAesKeyEncrypted = Rsa.encrypt(senderPubKey, newAesKeyStr)         
        var newAesIvEncrypted = Rsa.encrypt(senderPubKey, newAesIvStr)
    
        //println("securely send data back to the user who requested it ...")
        sender ! PictureListEncrypt(pictureListEncrypted, newAesKeyEncrypted, newAesIvEncrypted)     
        
      case GetAlbumFromServer(userId, senderPubKey) =>
        println("User: " + userId + " - Fetching its Album from the server and securely (AES-128 + RSA-1024) sending it to the requesting Actor.")
        
        var activityActor = system.actorOf(Props[ActivityActor], name = "activityActor" + getRandomId()) // delegate
        var futureGetAlbum = Patterns.ask(activityActor, GetAlbum2(userId, httpCookie), Timeout(Duration.create(5, "seconds")))
        var responseGetAlbum = Await.result(futureGetAlbum, Duration.create(5, "seconds")).asInstanceOf[HttpResponse]
        context.stop(activityActor)
        
        var jsonAlbum = responseGetAlbum.entity.data.asString.parseJson
        var album = jsonAlbum.convertTo[Album]
        //println("album from server -> "  + album)
        
        var albumPlainText = decryptAlbum(album, personalAESKey, personalAESIv)
        //println("albumPlainText by owner -> " + albumPlainText)
        
        // Encrypting album
        var newAesKey    = Aes.genRandKey()
        var newAesIv     = Aes.genRandIv()
        var newAesKeyStr = Base64.encodeBase64String(newAesKey.getEncoded)
        var newAesIvStr  = Base64.encodeBase64String(newAesIv)
        
        var albumEncrypted = encryptAlbum(albumPlainText, newAesKey, newAesIv) // encrypt album
        
        //println("Encrypting aesKey and aesIv using RSA public key of sender ...")
        var newAesKeyEncrypted = Rsa.encrypt(senderPubKey, newAesKeyStr)         
        var newAesIvEncrypted = Rsa.encrypt(senderPubKey, newAesIvStr)
    
        //println("securely send data back to the user who requested it ...")
        sender ! AlbumEncrypt(albumEncrypted, newAesKeyEncrypted, newAesIvEncrypted)          
    
      case "test" =>
        println("Inside test... ")
        var futureResponse: Future[HttpResponse] = pipeline(Get("http://localhost:8080/users/test"))
     
        futureResponse.foreach { 
          response =>
            println(response.entity.data.asString.parseJson.prettyPrint)
            
            
        }
      
      case msg: String   => println("Actor received unknown message '$msg'")
    }    
  }  
 
  
}

