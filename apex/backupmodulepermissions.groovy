import java.sql.ResultSet
import org.artifactory.storage.db.util.DbUtils
import org.artifactory.storage.db.util.JdbcHelper
import groovy.transform.Field
import au.com.bytecode.opencsv.CSVReader
import groovy.json.JsonBuilder
import groovy.sql.Sql
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.artifactory.util.AlreadyExistsException

@Field JdbcHelper JDBC_HELPER = ctx.beanForType(JdbcHelper.class)
@Field final String FILE_NAME = "Module_Permissions_Backup"
@Field final String FILE_PATH = "/etc/opt/jfrog/artifactory/"
@Field final String CSV_EXTENSION = ".csv"
@Field final String ZIP_EXTENSION = ".zip"
jobs {
	backUpModule(cron: "0 0 0/24 * * ?"){ takeBackup() }
}
executions{
	backupmodulepermission(httpMethod: 'GET', groups : 'power_users'){ params ->
		try{
			takeBackup()
			def json = [:]
			json["status"] = "Successfully Exported Module Permissions"
			message = new JsonBuilder(json).toPrettyString()
			status = 200
		}catch(e){
			log.error 'Failed to execute plugin', e
			message = 'Failed to execute plugin'
			status = 500
		}
	}
	importmodulepermission(httpMethod: 'POST', groups : 'power_users'){ params ->
		try{
			importMoudlePermission()
			def json = [:]
			json["status"] = "Successfully Imported Module Permissions"
			message = new JsonBuilder(json).toPrettyString()
			status = 200
		}catch(e){
			log.error 'Failed to execute plugin', e
			message = 'Failed to execute plugin'
			status = 500
		}
	}
	setupdb(httpMethod: 'POST', groups : 'power_users'){ params ->

		try{
			if(isSetupExisting()){	
				throw new AlreadyExistsException("Setup for Module permission already Existing")
			}
			setupDB()
			def json = [:]
			json["status"] = 'Module Permissions setup completed Successfully'
			message = new JsonBuilder(json).toPrettyString()
			status = 200
		}catch(AlreadyExistsException e){
			log.error 'Failed to execute plugin', e
			message = 'Failed to execute plugin : '+e
			status = 409
		}
		catch(Exception e){
			log.error 'Failed to execute plugin', e
			message = 'Failed to execute plugin : '+e
			status = 500
		}
	}
}
public void takeBackup(){
	ResultSet resultSet  = getModulePermission()
	File f = new File(FILE_PATH+FILE_NAME+CSV_EXTENSION)
	while(resultSet.next()){
		f.append("\n"+resultSet.getString(1)+";"+resultSet.getString(2)+";"+resultSet.getString(3)+";"+resultSet.getString(4)+";"+resultSet.getString(5))
	}
	DbUtils.close(resultSet)
	zipfile(f)
}

public void zipfile(File csvFile){
	ZipOutputStream zipFile = new ZipOutputStream(new FileOutputStream(FILE_PATH+FILE_NAME+ZIP_EXTENSION))
	if (csvFile.isFile()){
		zipFile.putNextEntry(new ZipEntry(csvFile.name))
		def buffer = new byte[csvFile.size()]
		csvFile.withInputStream {
			zipFile.write(buffer, 0, it.read(buffer))
		}
		zipFile.closeEntry()
	}
	csvFile.delete()
	zipFile.close()
}

public void importMoudlePermission(){
	def zip = new ZipFile(new File(FILE_PATH+FILE_NAME+ZIP_EXTENSION))
	zip.entries().each{
		if (!it.isDirectory()){
			def fOut = new File(FILE_PATH+ File.separator + it.name)
			new File(fOut.parent).mkdirs()
			def fos = new FileOutputStream(fOut)
			def buf = new byte[it.size]
			def len = zip.getInputStream(it).read(buf) //println zip.getInputStream(it).text
			fos.write(buf, 0, len)
			def reader = new CSVReader(new FileReader(new File(fOut.toString())))
			reader.readNext()
			def header = reader.readAll()
			reader.readNext()
			header.each {String[] csvEntries ->
				String[] entry = csvEntries[0].split(";")
				String name = entry[1]
				String user = entry[2]
				String author = entry[3]
				Long timestamp = Long.parseLong(entry[4])
				createPermission(name,user,author,timestamp)

			}
			fos.close()
			fOut.delete()
		}
	}
	zip.close()
}
private ResultSet getModulePermission(){
	return JDBC_HELPER.executeSelect("SELECT * from public.module_permissions")
}

private void createPermission(String modulename,String user_id,String authorizedBy,Long currentTime){

	JDBC_HELPER.executeUpdate("INSERT INTO public.module_permissions(module_name, user_id,authorized_by,created)VALUES (?, ?, ?, ?)",
			modulename,user_id,authorizedBy,currentTime)

}
private void setupDB(){

	JDBC_HELPER.executeUpdate("CREATE SEQUENCE public.module_permissions_permission_id_seq "+
			"INCREMENT 1 START 211 MINVALUE 1 MAXVALUE 9223372036854775807 CACHE 1")

	JDBC_HELPER.executeUpdate("CREATE TABLE public.module_permissions_lat "+
			"(permission_id integer NOT NULL DEFAULT nextval('module_permissions_permission_id_seq'::regclass),"+
			"module_name character varying(2048) COLLATE pg_catalog.default NOT NULL,"+
			"user_id character varying(64) COLLATE pg_catalog.default NOT NULL,"+
			"authorized_by character varying(64) COLLATE pg_catalog.default,"+
			"created bigint NOT NULL,CONSTRAINT module_permissions_pkey PRIMARY KEY (permission_id)"+
			")WITH (OIDS = FALSE)TABLESPACE pg_default")

	JDBC_HELPER.executeUpdate("CREATE INDEX module_permissions_module_name_idx "+
			"ON public.module_permissions USING btree "+
			"(module_name COLLATE pg_catalog.default) "+
			"TABLESPACE pg_default")

}

private boolean isSetupExisting(){
	def tableResultSet = JDBC_HELPER.executeSelect("SELECT EXISTS (SELECT 1 FROM   information_schema.tables WHERE table_name = 'module_permissions')");
	def sequenceResultSet = JDBC_HELPER.executeSelect("SELECT EXISTS (SELECT 1 FROM   information_schema.sequences WHERE sequence_name  = 'module_permissions_permission_id_seq')");
	def isSetupExisting = false
	def isTableExisting = "";
	def isSchemaExisting = "";
	while(tableResultSet.next()){
		isTableExisting = tableResultSet.getString(1)
	}
	while(sequenceResultSet.next()){
		isSchemaExisting = sequenceResultSet.getString(1)
	}

	if(isSchemaExisting.equalsIgnoreCase("t")||  isTableExisting.equalsIgnoreCase("t")){
		isSetupExisting = true
	}
	DbUtils.close(tableResultSet)
	DbUtils.close(sequenceResultSet)
	return isSetupExisting

}
