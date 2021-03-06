package scalaoauth2.provider

case class GrantHandlerResult(tokenType: String, accessToken: String, expiresIn: Long, refreshToken: Option[String], scope: Option[String])

trait GrantHandler {

  def handleRequest[U](request: Request, dataHandler: DataHandler[U]): GrantHandlerResult

  def issueAccessToken[U](dataHandler: DataHandler[U], authInfo: AuthInfo[U]): GrantHandlerResult = {
    val accessToken = dataHandler.createOrUpdateAccessToken(authInfo)
    GrantHandlerResult(
      "Bearer",
      accessToken.token,
      accessToken.expiresIn,
      authInfo.refreshToken,
      authInfo.scope
    )
  }
}

class RefreshToken(clientCredentialFetcher: ClientCredentialFetcher) extends GrantHandler {

  override def handleRequest[U](request: Request, dataHandler: DataHandler[U]): GrantHandlerResult = {
    val clientCredential = clientCredentialFetcher.fetch(request).getOrElse(throw new InvalidRequest("BadRequest"))
    val refreshToken = request.requireParam("refresh_token")
    val authInfo = dataHandler.findAuthInfoByRefreshToken(refreshToken).getOrElse(throw new InvalidGrant("NotFound"))
    if (authInfo.clientId != clientCredential.clientId) {
      throw new InvalidClient
    }

    val newAuthInfo = dataHandler.createOrUpdateAuthInfo(authInfo.user, authInfo.clientId, authInfo.scope).getOrElse(authInfo)
    issueAccessToken(dataHandler, newAuthInfo)
  }
}

class Password(clientCredentialFetcher: ClientCredentialFetcher) extends GrantHandler {

  override def handleRequest[U](request: Request, dataHandler: DataHandler[U]): GrantHandlerResult = {
    val clientCredential = clientCredentialFetcher.fetch(request).getOrElse(throw new InvalidRequest("BadRequest"))
    val username = request.requireParam("username")
    val password = request.requireParam("password")
    val user = dataHandler.findUser(username, password).getOrElse(throw new InvalidGrant())
    val scope = request.param("scope")
    val clientId = clientCredential.clientId
    val authInfo = dataHandler.createOrUpdateAuthInfo(user, clientId, scope).getOrElse(throw new InvalidGrant())
    if (authInfo.clientId != clientId) {
      throw new InvalidClient
    }

    issueAccessToken(dataHandler, authInfo)
  }
}

class ClientCredentials(clientCredentialFetcher: ClientCredentialFetcher) extends GrantHandler {

  override def handleRequest[U](request: Request, dataHandler: DataHandler[U]): GrantHandlerResult = {
    val clientCredential = clientCredentialFetcher.fetch(request).getOrElse(throw new InvalidRequest("BadRequest"))
    val clientSecret = clientCredential.clientSecret
    val clientId = clientCredential.clientId
    val user = dataHandler.findClientUser(clientId, clientSecret).getOrElse(throw new InvalidGrant())
    val scope = request.param("scope")
    val authInfo = dataHandler.createOrUpdateAuthInfo(user, clientId, scope).getOrElse(throw new InvalidGrant())

    issueAccessToken(dataHandler, authInfo)
  }

}

class AuthorizationCode(clientCredentialFetcher: ClientCredentialFetcher) extends GrantHandler {

  override def handleRequest[U](request: Request, dataHandler: DataHandler[U]): GrantHandlerResult = {
    val clientCredential = clientCredentialFetcher.fetch(request).getOrElse(throw new InvalidRequest("BadRequest"))
    val clientId = clientCredential.clientId
    val code = request.requireParam("code")
    val redirectUri = request.param("redirect_uri")
    val authInfo = dataHandler.findAuthInfoByCode(code).getOrElse(throw new InvalidGrant())
    if (authInfo.clientId != clientId) {
      throw new InvalidClient
    }

    if (authInfo.redirectUri.isDefined && authInfo.redirectUri != redirectUri) {
      throw new RedirectUriMismatch
    }

    issueAccessToken(dataHandler, authInfo)
  }

}
