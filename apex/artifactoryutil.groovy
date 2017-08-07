import org.artifactory.addon.AddonsManager
import org.artifactory.addon.composer.ComposerAddon
import org.artifactory.addon.composer.ComposerInfo
import org.artifactory.addon.composer.ComposerMetadataInfo
import org.artifactory.addon.npm.NpmAddon
import org.artifactory.addon.npm.NpmInfo
import org.artifactory.addon.npm.NpmMetadataInfo
import org.artifactory.addon.nuget.UiNuGetAddon
import org.artifactory.mime.MavenNaming
import org.artifactory.api.context.ContextHelper
import org.artifactory.api.maven.MavenArtifactInfo
import org.artifactory.nuget.NuMetaData
import org.artifactory.repo.LocalRepositoryConfiguration
import org.artifactory.repo.RepoPath

class artifactoryutil {

	public MavenArtifactInfo getMavenInfo(RepoPath repoPath){
		return MavenArtifactInfo.fromRepoPath(repoPath)
	}

	public NpmInfo getNPMInfo(RepoPath repoPath){
		AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class)
		NpmAddon npmAddon = addonsManager.addonByType(NpmAddon.class)
		NpmInfo npmInfo = null
		if (npmAddon != null) {
			// get npm meta data
			NpmMetadataInfo npmMetaDataInfo = npmAddon.getNpmMetaDataInfo(repoPath)
			npmInfo =  npmMetaDataInfo.getNpmInfo()
		}
		return npmInfo
	}

	public NuMetaData getNugetInfo(RepoPath repoPath){
		AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class);
		UiNuGetAddon uiNuGetAddon = addonsManager.addonByType(UiNuGetAddon.class)
		NuMetaData nugetSpecMetaData = null
		if (uiNuGetAddon != null) {
			nugetSpecMetaData = uiNuGetAddon.getNutSpecMetaData(repoPath);
			def id = nugetSpecMetaData.getId()
		}
		return nugetSpecMetaData
	}

	public ComposerInfo getComposerInfo(RepoPath repoPath){
		AddonsManager addonsManager = ContextHelper.get().beanForType(AddonsManager.class)
		ComposerAddon composerAddon = addonsManager.addonByType(ComposerAddon.class)
		def composerInfo = null
		if(composerAddon != null ){
			ComposerMetadataInfo composerMetadataInfo =  composerAddon.getComposerMetadataInfo(repoPath)
			composerInfo = composerMetadataInfo.getComposerInfo()
		}
		return composerInfo
	}
	public String getMavenVersion(currentLayout, propName) {
		if(currentLayout.folderIntegrationRevision.equals(SNAPSHOT))
			return currentLayout."$propName" + '-' + SNAPSHOT
		else
			return currentLayout."$propName"
	}
	private Map getModuleName(LocalRepositoryConfiguration repoConfig,RepoPath repoPath) {
		def nameAndVersionMap = [:]
		String moduleName = ""
		String version= ""
		def artUtil  = new artifactoryutil()
		if(repoConfig.getPackageType().equalsIgnoreCase("Maven")){
			def artifactInfo  = artUtil.getMavenInfo(repoPath)
			if(!artifactInfo.hasClassifier()){
				moduleName = artifactInfo.getArtifactId()
			}
				
			if(MavenNaming.isSnapshot(repoPath.getPath())){
				version = artifactInfo.getVersion().split("-")[0]+MavenNaming.SNAPSHOT_SUFFIX
			}else{
				version = artifactInfo.getVersion()
			}
		}
		if(repoConfig.getPackageType().equalsIgnoreCase("Npm")){
			def npmInfo = artUtil.getNPMInfo(repoPath)
			moduleName = npmInfo.getName()
			version = npmInfo.getVersion()
		}
		if(repoConfig.getPackageType().equalsIgnoreCase("NuGet")){
			def nugetInfo = artUtil.getNugetInfo(repoPath)
			moduleName = nugetInfo.getId()
			version = nugetInfo.getVersion()
		}
		if(repoConfig.getPackageType().equalsIgnoreCase("Composer")){
			def composerInfo = artUtil.getComposerInfo(repoPath)
			moduleName = composerInfo.getName()
			version = composerInfo.getVersion()
		}
		nameAndVersionMap.put("module.name", moduleName)
		nameAndVersionMap.put("module.baseRevision", version)
		return nameAndVersionMap
	}
	public static void main(String[] args){
	}
}
