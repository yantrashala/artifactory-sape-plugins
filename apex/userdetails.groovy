import groovy.json.JsonBuilder
import org.artifactory.addon.sso.ArtifactoryCrowdClient
import org.artifactory.spring.InternalArtifactoryContext
import com.atlassian.crowd.search.builder.Restriction
import com.atlassian.crowd.search.query.entity.restriction.PropertyImpl
import com.atlassian.crowd.service.client.CrowdClient

executions{

	/* 
	 * artifactory/api/plugins/execute/userdetails?params=email=<email_id>
	 * artifactory/api/plugins/execute/userdetails?params=username=<nt_id>
	 * executes the closure if the request is from 'users' group
	 * Parameters:
	 * username (String) - NT id of the user
	 * email (String) -  email of the user
	 */
	userdetails(httpMethod: 'GET', groups : 'users'){ params ->
		try {
			//getting username(NT id) or email from httpRequest
			String username = params?.get('username')?.getAt(0)
			String email = params?.get('email')?.getAt(0)

			log.debug("username : "+username)
			log.debug("email : "+email)
			//getting ArtifactoryCrowdClient bean from InternalArtifactoryContext(ctx) 
			def artifactoryCrowdClient= ctx.beanForType(ArtifactoryCrowdClient.class);
			if(artifactoryCrowdClient.isCrowdAvailable()) {
				def crowdClient = artifactoryCrowdClient.getHttpAuthenticator().getClient();
				def json = [:]
				
				/*if username(NT id) is not null then executes getUserInfofromNtId method
				 * and if email is not null and username is null then executes 
				 * getUserInfofromEmail method
				 */
				if(username != null){
					json = getUserInfofromNtId(crowdClient,username)
				} else if(email != null && username == null) {
					json = getUserInfofromEmail(crowdClient,email)
				}

				message = new JsonBuilder(json).toPrettyString()
				status = 200
			} else {
				message = "crowd client is not available"
				status = 500
			}

		}
		catch (Exception ex) {
			log.error("Error:"+ex);
			message = "Error occured while fetching user details"
			status = 500
		}
	}
}
/*
 * retrieves userinfo using NT id
 * Parameters :
 * crowClient - CrowdClient Object
 * ntid - (String) ntid of the user
 * returns - (Map) userInfo
 */
private Map getUserInfofromNtId(CrowdClient crowdClient, String ntid){
	def userInfo = [:]
	def userEntity = crowdClient.getUser(ntid);
	userInfo['displayName'] = userEntity.getDisplayName()
	userInfo['emailId'] = userEntity.getEmailAddress()
	userInfo['loginID'] = userEntity.getName()

	return userInfo
}
/*
 * retrieves userinfo using email
 * Parameters :
 * crowClient - CrowdClient Object
 * email - (String) email of the user
 * returns - (Map) userInfo
 */
private Map getUserInfofromEmail(CrowdClient crowdClient, String email){
	def userInfo = [:]
	def emailProperty = new PropertyImpl("email", String.class)
	def emailRestriction = Restriction.on(emailProperty).exactlyMatching(email)
	
	//Searches for usernames matching the searchRestriction criteria
	// and returns List of users satisfying the search restriction
	def usersList = crowdClient.searchUsers(emailRestriction,0,1)
	boolean userListIsEmpty = usersList.isEmpty()
	userInfo['displayName'] = userListIsEmpty ?  email : usersList.getAt(0).getDisplayName()
	userInfo['emailId'] = userListIsEmpty ?  email : usersList.getAt(0).getEmailAddress()
	userInfo['loginID'] = userListIsEmpty ? email : usersList.getAt(0).getName()

	return userInfo
}
