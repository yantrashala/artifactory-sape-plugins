import java.util.ArrayList
import java.util.HashSet
import java.util.List
import java.util.Set

import javax.servlet.http.HttpServletResponse

import org.artifactory.api.repo.RepositoryService
import org.artifactory.api.security.AclService
import org.artifactory.factory.InfoFactory
import org.artifactory.factory.InfoFactoryHolder
import org.artifactory.fs.FileLayoutInfo
import org.artifactory.fs.ItemInfo
import org.artifactory.security.AceInfo
import org.artifactory.security.AclInfo
import org.artifactory.security.ArtifactoryPermission
import org.artifactory.security.MutableAceInfo
import org.artifactory.security.MutableAclInfo
import org.artifactory.security.MutablePermissionTargetInfo
import org.artifactory.security.PermissionTargetInfo
import org.artifactory.ui.rest.model.admin.security.permissions.PermissionTargetModel
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.tabs.permission.EffectivePermission
import org.artifactory.ui.rest.model.utils.repositories.RepoKeyType;
import org.artifactory.util.AlreadyExistsException
import org.artifactory.util.ZipUtils

import groovy.transform.Field

@Field final String PROPERTY_PREFIX = 'layout.'
@Field final String PERMS_TARGET_PREFIX = 'appx_'

storage {
	afterCreate { ItemInfo item ->
		/*
		 * 
		 * if (layout.isValid()) id = layout.module
		 else id = (item.name =~ '^(?:\\D[^.]*\\.)+')[0] - ~'\\.$'
		 */
		
		log.info("getCreatedBy======= ${item.createdBy}")
		log.info("getModifiedBy======= ${item.modifiedBy}")

		if(item.repoPath.isFile()) {

			RepositoryService repoService = ctx.beanForType(RepositoryService.class);

			def filePath = item.repoPath.path.toLowerCase()

			if (ZipUtils.isZipFamilyArchive(filePath) || ZipUtils.isTarArchive(filePath) ||
			ZipUtils.isTgzFamilyArchive(filePath) || ZipUtils.isGzCompress(filePath)) {

				FileLayoutInfo currentLayout = repositories.getLayoutInfo(item.repoPath)
				// Gets the actual layout of the repository the artifact is deployed to
				def id = null
				if (currentLayout.isValid()) id = currentLayout.module
				else id = (item.name =~ '^(?:\\[^.]*\\.)+')[0] - ~'\\.$'

				log.info "id=========== ${id}"
				
				/*
				 * 1. Check whether the module is a new module
				 * 2. If new module, create permissionTarget and add deploying user to it
				 * 3. If old module, check if the deploying user is part of the permissionTarget
				 * 4. Add executions to allow POST, so that users with permission can add other users
				 *  to the permission target
				 */
					

				//				AuthorizationService authService = ctx.beanForType(AuthorizationService.class)
				AclService aclService = ctx.beanForType(AclService.class)


				def permissionTargetName = PERMS_TARGET_PREFIX + item.repoKey + "_" + id

				log.info("permissionTargetName======= ${permissionTargetName}")

				
				AclInfo aclInfo = aclService.getAcl('test');
				// populate permission model data
				PermissionTargetInfo permission = aclInfo.getPermissionTarget();
				
				log.info ("permission==== repokeys"+permission.getRepoKeys())
				log.info ("permission==== includes"+permission.getIncludes())
				log.info ("permission==== getExcludes"+permission.getExcludes())
				log.info ("permission==== getIncludesPattern"+permission.getIncludesPattern())
				log.info ("permission==== getExcludesPattern"+permission.getExcludesPattern())
				
				aclInfo.getAces().stream().filter{ ace -> 
					!ace.isGroup()
				}.forEach{ aceInfo ->
					
					log.info("aceInfo.getPrincipal===="+aceInfo.getPrincipal())
				};
				
				//ArtifactoryPermission permission = new ArtifactoryPermission()
				/*aclService.getPermissionTargets()
				
				if(aclService.permissionTargetExists(permissionTargetName)) {
					log.info "${permissionTargetName} exists"
				} else {
					log.info "${permissionTargetName} does not exists"
					//createPermissionTarget(permissionTargetName)
				}
				*/
				//aclService.getPermissionTargets()





				//log.info "ID===   ${id}"
			}
		}



	}
}
/*
def createPermissionTarget(permissionTargetName) {
	try {

		// check if anny remote/local/distribution is set and filter repo keys list accordingly
		//filteredRepoKey(permissionTarget);
		InfoFactory infoFactory = InfoFactoryHolder.get();
		// create new acl
		MutableAclInfo mutableAclInfo = infoFactory.createAcl();
		List<RepoKeyType> filteredRepoKeys = new ArrayList<>();
		filteredRepoKeys.add(new RepoKeyType("ANY LOCAL", "ANY LOCAL"));
		
		
		
		//############################
		
		MutablePermissionTargetInfo permission = infoFactory.createPermissionTarget();
		
		permission.setExcludes(permissionTarget.getExclude());
		permission.setExcludesPattern(permissionTarget.getExcludePattern());
		permission.setIncludes(permissionTarget.getIncludes());
		permission.setIncludesPattern(permissionTarget.getIncludePattern());
		permission.setName(permissionTarget.getName());
		if (!permissionTarget.getRepoKeys().isEmpty()) {
			List<String> keys = new ArrayList<>();
			permissionTarget.getRepoKeys().forEach(repoKeyType -> {
				String repoKey = repoKeyType.getRepoKey();
				if (repoKeyType.getType().equals("remote")){
						repoKey = repoKey+"-cache";
						}
				keys.add(repoKey);
			});
			permission.setRepoKeys(keys);
		}
		
		
		//############################
		Set<AceInfo> aclInfos = new HashSet<>();

		// update user permission
		permissionTarget.getUsers().forEach(permissionModel -> {
			MutableAceInfo ace = infoFactory.createAce(permissionModel.getPrincipal(), false, permissionModel.getMask());
			// update ace permissions
			updateAcePermission(permissionModel, ace);
			aclInfos.add(ace);
		});
		mutableAclInfo.setAces(aclInfos);
		
		
		
		mutableAclInfo.setPermissionTarget(permission);
		// update permission target data
		//updatePermissionTarget(permissionTarget, infoFactory, mutableAclInfo);
		// update user and groups permissions
		//updateAcesPermissions(permissionTarget, infoFactory, mutableAclInfo);
		// update acl
		aclService.createAcl(mutableAclInfo);
		//response.info("Successfully Created permission target '" + permissionTarget.getName() + "'");
	}
	catch (Exception e) {
		if (e instanceof AlreadyExistsException) {
			//response.error("Permission target '" + permissionTarget.getName() + "' already exists");
		} else {
			//response.error("Unexpected error has occurred please review the logs");
		}
		//log.debug(e.toString());
	}
}

*/
