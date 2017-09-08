import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

import org.apache.catalina.ssi.ExpressionParseTree.GreaterThanNode
import org.sonar.wsclient.Sonar
import org.sonar.wsclient.SonarClient
import org.sonar.wsclient.base.HttpException
import org.sonar.wsclient.services.ResourceQuery
import groovy.transform.Field
import searchbykeyword

@Field final String FILE_PATH = ctx.getArtifactoryHome().getEtcDir().toString()+"/"
@Field final Map COLOR_STATUS = [1:"#4c1",2:"#dfb317",3:"#fe7d37",4:"#e05d44"]
@Field final File SVG_FILE = new File(FILE_PATH+"vectorpaint.svg")
@Field final File SONAR_FILE = new File(FILE_PATH+"sonar.properties")

executions{

	sonar(httpMethod: 'GET', groups : 'users'){ params ->
		JsonSlurper slurper = new JsonSlurper()
		try{
			def componentKey =  params?.get('compkey').getAt(0) as String
			SonarClient client  = getSonarClient()
			if(client == null){
				throw new NullPointerException("SonarClient cannot be null")
			}
			def coverageParams = ["componentKey":componentKey,"metricKeys":"coverage"]
			def qualityParams = ["componentKey":componentKey,"metricKeys":"quality_gate_details"]
			def measures = client.get("api/measures/component?", coverageParams)
			def navigation = client.get("api/navigation/component?",coverageParams)
			def quality = client.get("api/measures/component?", qualityParams)
			def measureslist = slurper.parseText(measures).get("component").get("measures")
			def version = slurper.parseText(navigation).get("version")
			def qualitylist  = slurper.parseText(quality).get("component").get("measures").get(0).get("value")
			def qualitystatus = slurper.parseText(qualitylist).getAt("level") as String
			def coveragePercent = measureslist.size()> 0 ?measureslist.get(0).get("value")  : "unknown"
			def colorStatus = COLOR_STATUS.get(4)
			if(!coveragePercent.equals("unknown")){
				coveragePercent = Float.parseFloat(coveragePercent)
				if(coveragePercent > 80)
					colorStatus = COLOR_STATUS.get(1)
				else if(coveragePercent > 60)
					colorStatus = COLOR_STATUS.get(2)
				else if(coveragePercent > 40)
					colorStatus = COLOR_STATUS.get(3)
				coveragePercent = coveragePercent+"%"
			}
			def finalres = ""
			def colorStatusforQuality= ""
			if(qualitystatus.equalsIgnoreCase("ERROR")){
				finalres = "failed"
				colorStatusforQuality = COLOR_STATUS.get(4)
			}else if(qualitystatus.equalsIgnoreCase("WARN")){
				finalres = "warning"
				colorStatusforQuality = COLOR_STATUS.get(2)
			}else{
				finalres = "passed"
				colorStatusforQuality = COLOR_STATUS.get(1)
			}
			String svgScript = getSvgBody(version,finalres,coveragePercent,colorStatus,colorStatusforQuality)
			message = svgScript
			status = 200
		}
		catch(e){
			log.error 'Error', e
			message = e.getMessage()
			status = 500
		}
	}
}

String getSvgBody(String version,String quality,String coverage,
		String coverageColor,String qualityColor){
	String message = ""
	try{
		message = SVG_FILE.getText()
	}catch(FileNotFoundException e){
		e.printStackTrace()
	}
	message = message.replaceAll("###Module_Version###", version)
	message = message.replaceAll("###Quality###", quality)
	message = message.replaceAll("###Coverage###", coverage)
	message = message.replaceAll("###colorStatus###", coverageColor)
	message = message.replaceAll("###colorStatusforQuality###", qualityColor)
	return message
}
public SonarClient getSonarClient(){
	SonarClient client = null
	try{
		Properties properties = new Properties()
		SONAR_FILE.withInputStream { properties.load(it) }
		String sonarurl = properties."sonarurl"
		String token = properties."token"
		log.debug("sonarurl : "+sonarurl)
		client = SonarClient.builder().
				url(sonarurl.trim()).
				login(token.trim()).password("").build()
	}catch(HttpException  e){
		e.printStackTrace()
	}
	return client
}
