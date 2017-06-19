import groovy.json.JsonBuilder

import org.artifactory.addon.sso.ArtifactoryCrowdClient
import org.artifactory.spring.InternalArtifactoryContext
import com.atlassian.crowd.search.builder.Restriction
import com.atlassian.crowd.search.query.entity.restriction.PropertyImpl
import com.atlassian.crowd.service.client.CrowdClient


executions{
	userdetails(httpMethod: 'GET', groups : 'users'){ params ->
		try {
			String username = params?.get('username')?.getAt(0)
			String email = params?.get('email')?.getAt(0)
			log.debug("username : "+username)
			log.debug("email : "+email)
			def artifactoryCrowdClient= ctx.beanForType(ArtifactoryCrowdClient.class);
			if(artifactoryCrowdClient.isCrowdAvailable()) {
				def crowdClient = artifactoryCrowdClient.getHttpAuthenticator().getClient();
				def json = [:]
				if(username != null){			
					json = getUserInfofromNtId(crowdClient,username)
				}
				if(email != null && username == null){
					json = getUserInfofromEmail(crowdClient,email)
					
				}
				
				message = new JsonBuilder(json).toPrettyString()
				status = 200
			} else {
				message = "crowdClient is Not Available()"
				status = 500
			}

		}
		catch (Exception ex) {
			message = "error: " + ex.message
			status = 500
		}
	}
}

public Map getUserInfofromNtId(CrowdClient  crowdClient,String ntid){
	def userInfo = [:]
	def userEntity = crowdClient.getUser(ntid);
	userInfo['displayName'] = userEntity.getDisplayName()
	userInfo['emailId'] = userEntity.getEmailAddress()
	userInfo['loginID'] = userEntity.getName()
	return userInfo
}
public Map getUserInfofromEmail(CrowdClient  crowdClient,String email){
	def userInfo = [:]
	def emailProperty = new PropertyImpl("email", String.class)
	def emailRestriction = Restriction.on(emailProperty).exactlyMatching(email)
	def usersList = crowdClient.searchUsers(emailRestriction,0,1)
	userInfo['displayName'] = usersList.size() > 0 ? usersList.getAt(0).getDisplayName() : email
	userInfo['emailId'] = usersList.size() > 0 ? usersList.getAt(0).getEmailAddress() : email
	userInfo['loginID'] = usersList.size() > 0 ? usersList.getAt(0).getName() : email

	return userInfo
}
