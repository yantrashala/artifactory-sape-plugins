import groovy.json.JsonBuilder

import org.artifactory.addon.sso.ArtifactoryCrowdClient
import com.atlassian.crowd.search.builder.Restriction
import com.atlassian.crowd.search.query.entity.restriction.PropertyImpl


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
				def usersList = []
				def userEntity
				def json = [:]
				if(username != null){			
					userEntity = crowdClient.getUser(username);
					json['displayName'] = userEntity.getDisplayName()
					json['emailId'] = userEntity.getEmailAddress()
					json['loginID'] = userEntity.getName()
				}
				if(email != null && username == null){
					def emailProperty = new PropertyImpl("email", String.class)
					def emailRestriction = Restriction.on(emailProperty).exactlyMatching(email)
					usersList = crowdClient.searchUsers(emailRestriction ,0,1)
					
					json['displayName'] = usersList.size() > 0 ? usersList.getAt(0).getDisplayName() : email
					json['emailId'] = usersList.size() > 0 ? usersList.getAt(0).getEmailAddress() : email
					json['loginID'] = usersList.size() > 0 ? usersList.getAt(0).getName() : email
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


