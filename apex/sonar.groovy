import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

import org.apache.catalina.ssi.ExpressionParseTree.GreaterThanNode
import org.sonar.wsclient.Sonar
import org.sonar.wsclient.SonarClient
import org.sonar.wsclient.base.HttpException
import org.sonar.wsclient.services.ResourceQuery
import groovy.transform.Field

@Field final String FILE_PATH = ctx.getArtifactoryHome().getEtcDir().toString()+"/"
@Field final Map COLOR_STATUS = [1:"#4c1",2:"#dfb317",3:"#fe7d37",4:"#e05d44"]
@Field final File SVG_FILE = new File(FILE_PATH+"sonarshield.svg")
@Field final File SONAR_FILE = new File(FILE_PATH+"sonar.properties")
@Field String SVG_TEXT = null

executions{
	/* /shields/<module_name>/<module_version>/sonar.svg?compkey=<module_key> ,
	 * executes the closure if the request is from 'users' group
	 */
	sonar(httpMethod: 'GET', groups : 'users'){ params ->
		JsonSlurper slurper = new JsonSlurper()

		//load svg file if SVG_TEXT is null
		if(SVG_TEXT == null) {
			loadsvgfile()
			if(SVG_TEXT == null) {
				message = "Error occurred in loading svg"
				status = 500
				return
			}
		}

		//setting default value for coverage,quality and version
		def coverage = "unknown"
		def quality = "unknown"
		def version = ""

		try{
			def componentKey =  params?.get('compkey').getAt(0) as String
			SonarClient client  = getSonarClient()

			// sending API requests to Sonar Server
			def coverageParams = ["componentKey":componentKey,"metricKeys":"coverage"]
			def qualityParams = ["componentKey":componentKey,"metricKeys":"quality_gate_details"]
			def measures = client.get("api/measures/component?", coverageParams)
			def navigation = client.get("api/navigation/component?",coverageParams)
			def qualityData = client.get("api/measures/component?", qualityParams)

			// Parse response
			def measureslist = slurper.parseText(measures).get("component").get("measures")
			version = slurper.parseText(navigation).get("version")
			def qualitylist  = slurper.parseText(qualityData).get("component").get("measures").get(0).get("value")
			quality = slurper.parseText(qualitylist).getAt("level") as String
			coverage = measureslist.size()> 0 ?measureslist.get(0).get("value")  : "unknown"

		}
		catch(e){
			log.error 'Error in sonar svg', e
		}

		String svgScript = getSvgBody(version,quality,coverage)
		message = svgScript
		status = 200
	}
}

private void loadsvgfile() {
	//Getting SVG_FILE content as text
	try{
		SVG_TEXT = SVG_FILE.getText()
	}catch(FileNotFoundException e){
		log.error("Error in getSvgBody:"+e)
		e.printStackTrace()
	}
}

private String getSvgBody(def version, def quality, def coverage){
	String message = SVG_TEXT

	// Applying color rules
	def coverageColor = COLOR_STATUS.get(4)
	if(!coverage.equals("unknown")){
		coverage = Float.parseFloat(coverage)
		if(coverage > 80)
			coverageColor = COLOR_STATUS.get(1)
		else if(coverage > 60)
			coverageColor = COLOR_STATUS.get(2)
		else if(coverage > 40)
			coverageColor = COLOR_STATUS.get(3)
		coverage = coverage+"%"
	}

	def qualityColor= ""
	if(quality.equalsIgnoreCase("ERROR")){
		quality = "failed"
		qualityColor = COLOR_STATUS.get(4)
	}else if(quality.equalsIgnoreCase("WARN")){
		quality = "warning"
		qualityColor = COLOR_STATUS.get(2)
	}else if(quality.equalsIgnoreCase("unknown")){
		quality = "unknown"
		qualityColor = COLOR_STATUS.get(4)
	}else{
		quality = "passed"
		qualityColor = COLOR_STATUS.get(1)
	}

	message = message.replaceAll("###Module_Version###", version)
	message = message.replaceAll("###Quality###", quality)
	message = message.replaceAll("###Coverage###", coverage)
	message = message.replaceAll("###colorStatus###", coverageColor)
	message = message.replaceAll("###colorStatusforQuality###", qualityColor)
	return message
}

private SonarClient getSonarClient(){
	SonarClient client = null
	try{
		Properties properties = new Properties()
		//reading SONAR_FILE properties to get sonar url and login token
		SONAR_FILE.withInputStream { properties.load(it) }
		String sonarurl = properties."sonarurl"
		String token = properties."token"
		log.debug("sonarurl : "+sonarurl)
		client = SonarClient.builder().
				url(sonarurl.trim()).
				login(token.trim()).password("").build()
	}catch(HttpException  e){
		log.error("Error in getSonarClient:"+e)
	}
	return client
}
