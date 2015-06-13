package com.dhpcs.liquidity.models

import java.security.KeyPairGenerator
import java.util.UUID

import com.dhpcs.jsonrpc.{JsonRpcNotificationMessage, JsonRpcRequestMessage, JsonRpcResponseMessage}
import com.google.common.io.BaseEncoding
import org.scalactic.Uniformity
import org.scalatest.OptionValues._
import org.scalatest._
import play.api.data.validation.ValidationError
import play.api.libs.json._

class MessageSpec extends FunSpec with Matchers {

  // TODO: s/decode/read/, /s/encode/write/, make output less verbose, unify those changes with FormatBehaviors

  def ordered[U] = new Uniformity[JsResult[U]] {

    override def normalizedCanHandle(b: Any) = b match {
      case _: JsError => true
      case _ => false
    }

    override def normalizedOrSame(b: Any) = b match {
      case j: JsError => normalized(j)
      case _ => b
    }

    override def normalized(a: JsResult[U]) = a match {
      case jsError: JsError =>
        jsError.copy(
          errors = jsError.errors.sortBy { case (jsPath: JsPath, _) => jsPath.toJsonString }
        )
      case _ => a
    }

  }

  def commandDecodeError(jsonRpcRequestMessage: JsonRpcRequestMessage, maybeJsError: Option[JsError]) =
    it(s"$jsonRpcRequestMessage should fail to decode with error $maybeJsError") {
      val maybeCommandJsResult = Command.readCommand(jsonRpcRequestMessage)
      maybeJsError.fold(
        maybeCommandJsResult shouldBe empty
      )(
          jsError => {
            maybeCommandJsResult.value should equal(jsError)(after being ordered[Command])
          }
        )
    }

  def commandDecode(implicit jsonRpcRequestMessage: JsonRpcRequestMessage, command: Command) =
    it(s"$jsonRpcRequestMessage should decode to $command") {
      Command.readCommand(jsonRpcRequestMessage) should be(Some(JsSuccess(command)))
    }

  def commandEncode(implicit command: Command, id: Either[String, Int], jsonRpcRequestMessage: JsonRpcRequestMessage) =
    it(s"$command should encode to $jsonRpcRequestMessage") {
      Command.writeCommand(command, id) should be(jsonRpcRequestMessage)
    }

  describe("A Command") {
    describe("with an invalid method") {
      it should behave like commandDecodeError(
        JsonRpcRequestMessage(
          "invalidMethod",
          Right(Json.obj()),
          Right(0)
        ),
        None
      )
    }
    describe("of type CreateZone") {
      describe("with params of the wrong type") {
        it should behave like commandDecodeError(
          JsonRpcRequestMessage(
            "createZone",
            Left(Json.arr()),
            Right(0)
          ),
          Some(
            JsError(List(
              (__, List(ValidationError("command parameters must be named")))
            ))
          )
        )
      }
      describe("with empty params") {
        it should behave like commandDecodeError(
          JsonRpcRequestMessage(
            "createZone",
            Right(Json.obj()),
            Right(0)
          ),
          Some(
            JsError(List(
              (__ \ "name", List(ValidationError("error.path.missing"))),
              (__ \ "zoneType", List(ValidationError("error.path.missing")))
            ))
          )
        )
      }
      implicit val createZone = CreateZone(
        "Dave's zone",
        "test"
      )
      implicit val id = Right(0)
      implicit val jsonRpcRequestMessage = JsonRpcRequestMessage(
        "createZone",
        Right(
          Json.obj(
            "name" -> "Dave's zone",
            "zoneType" -> "test"
          )
        ),
        Right(0)
      )
      it should behave like commandDecode
      it should behave like commandEncode
    }
  }

  def commandResponseDecodeError(jsonRpcResponseMessage: JsonRpcResponseMessage,
                                 method: String,
                                 jsError: JsError) =
    it(s"$jsonRpcResponseMessage should fail to decode with error $jsError") {
      (CommandResponse.readCommandResponse(jsonRpcResponseMessage, method)
        should equal(jsError))(after being ordered[CommandResponse])
    }

  def commandResponseDecode(implicit jsonRpcResponseMessage: JsonRpcResponseMessage,
                            method: String,
                            commandResponse: CommandResponse) =
    it(s"$jsonRpcResponseMessage should decode to $commandResponse") {
      CommandResponse.readCommandResponse(jsonRpcResponseMessage, method) should be(JsSuccess(commandResponse))
    }

  def commandResponseEncode(implicit commandResponse: CommandResponse,
                            id: Either[String, Int],
                            jsonRpcResponseMessage: JsonRpcResponseMessage) =
    it(s"$commandResponse should encode to $jsonRpcResponseMessage") {
      CommandResponse.writeCommandResponse(commandResponse, id, Json.obj()) should be(jsonRpcResponseMessage)
    }

  describe("A CommandResponse") {
    describe("of type ZoneCreated") {
      describe("with empty params") {
        it should behave like commandResponseDecodeError(
          JsonRpcResponseMessage(
            Right(Json.obj()),
            Some(Right(0))
          ),
          "createZone",
          JsError(List(
            (__ \ "zoneId", List(ValidationError("error.path.missing")))
          ))
        )
      }
    }
    implicit val zoneCreated = ZoneCreated(
      ZoneId(UUID.fromString("158842d1-38c7-4ad3-ab83-d4c723c9aaf3"))
    )
    implicit val id = Right(0)
    implicit val jsonRpcResponseMessage = JsonRpcResponseMessage(
      Right(
        Json.obj(
          "zoneId" -> "158842d1-38c7-4ad3-ab83-d4c723c9aaf3"
        )
      ),
      Some(
        Right(0)
      )
    )
    implicit val method = "createZone"
    it should behave like commandResponseDecode
    it should behave like commandResponseEncode
  }

  def notificationDecodeError(jsonRpcNotificationMessage: JsonRpcNotificationMessage, maybeJsError: Option[JsError]) =
    it(s"$jsonRpcNotificationMessage should fail to decode with error $maybeJsError") {
      val maybeNotificationJsResult = Notification.readNotification(jsonRpcNotificationMessage)
      maybeJsError.fold(
        maybeNotificationJsResult shouldBe empty
      )(
          jsError => {
            maybeNotificationJsResult.value should equal(jsError)(after being ordered[Notification])
          }
        )
    }

  def notificationDecode(implicit jsonRpcNotificationMessage: JsonRpcNotificationMessage, notification: Notification) =
    it(s"$jsonRpcNotificationMessage should decode to $notification") {
      Notification.readNotification(jsonRpcNotificationMessage) should be(Some(JsSuccess(notification)))
    }

  def notificationEncode(implicit notification: Notification, jsonRpcNotificationMessage: JsonRpcNotificationMessage) =
    it(s"$notification should encode to $jsonRpcNotificationMessage") {
      Notification.writeNotification(notification) should be(jsonRpcNotificationMessage)
    }

  describe("A Notification") {
    describe("with an invalid method") {
      it should behave like notificationDecodeError(
        JsonRpcNotificationMessage(
          "invalidMethod",
          Right(Json.obj())
        ),
        None
      )
    }
    describe("of type Notification") {
      describe("with params of the wrong type") {
        it should behave like notificationDecodeError(
          JsonRpcNotificationMessage(
            "zoneState",
            Left(Json.arr())
          ),
          Some(
            JsError(List(
              (__, List(ValidationError("notification parameters must be named")))
            ))
          )
        )
      }
      describe("with empty params") {
        it should behave like notificationDecodeError(
          JsonRpcNotificationMessage(
            "zoneState",
            Right(Json.obj())
          ),
          Some(
            JsError(List(
              (__ \ "zoneId", List(ValidationError("error.path.missing"))),
              (__ \ "zone", List(ValidationError("error.path.missing")))
            ))
          )
        )
      }
      val publicKeyBytes = KeyPairGenerator.getInstance("RSA").generateKeyPair.getPublic.getEncoded
      implicit val zoneState = ZoneState(
        ZoneId(UUID.fromString("a52e984e-f0aa-4481-802b-74622cb3f6f6")),
        Zone(
          "Dave's zone",
          "test",
          Map(
            MemberId(UUID.fromString("fa781d33-368f-42a5-9c64-0e4b43381c37")) ->
              Member("Dave", PublicKey(publicKeyBytes))
          ),
          Map(
            AccountId(UUID.fromString("f2f4613c-0645-4dec-895b-2812382f4523")) ->
              Account("Dave's account", Set(MemberId(UUID.fromString("fa781d33-368f-42a5-9c64-0e4b43381c37"))))
          ),
          Map(
            TransactionId(UUID.fromString("65b1711c-5747-452c-8975-3f0d36e9efa6")) ->
              Transaction(
                "Dave's lottery win",
                AccountId(UUID.fromString("80ccbec2-79a4-4cfa-8e97-f33fac2aa5ba")),
                AccountId(UUID.fromString("f2f4613c-0645-4dec-895b-2812382f4523")),
                BigDecimal(1000000),
                1433611420487L
              )
          ),
          1433611420487L
        )
      )
      implicit val jsonRpcNotificationMessage = JsonRpcNotificationMessage(
        "zoneState",
        Right(
          Json.obj(
            "zoneId" -> "a52e984e-f0aa-4481-802b-74622cb3f6f6",
            "zone" -> Json.parse( s"""{"name":"Dave's zone","type":"test","members":{"fa781d33-368f-42a5-9c64-0e4b43381c37":{"name":"Dave","publicKey":"${BaseEncoding.base64.encode(publicKeyBytes)}"}},"accounts":{"f2f4613c-0645-4dec-895b-2812382f4523":{"name":"Dave's account","owners":["fa781d33-368f-42a5-9c64-0e4b43381c37"]}},"transactions":{"65b1711c-5747-452c-8975-3f0d36e9efa6":{"description":"Dave's lottery win","from":"80ccbec2-79a4-4cfa-8e97-f33fac2aa5ba","to":"f2f4613c-0645-4dec-895b-2812382f4523","amount":1000000,"created":1433611420487}},"lastModified":1433611420487}""")
          )
        )
      )
      it should behave like notificationDecode
      it should behave like notificationEncode
    }
  }

}