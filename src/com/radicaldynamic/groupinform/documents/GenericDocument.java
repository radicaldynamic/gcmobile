package com.radicaldynamic.groupinform.documents;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.ektorp.Attachment;
import org.ektorp.support.CouchDbDocument;

import android.util.Log;

import com.radicaldynamic.groupinform.application.Collect;
import com.radicaldynamic.groupinform.utilities.StringUtils;

@SuppressWarnings("serial")
public class GenericDocument extends CouchDbDocument
{
    private static final String t = "GenericDocument: ";

    public static final String DATETIME = "yyyy/MM/dd HH:mm:ss Z"; 
    
    private Integer authoredBy;
    private Integer updatedBy;
    
    private String dateCreated;
    private String dateUpdated;
    
    private String type;
    
    private Integer documentVersion;
    
    /*
     * TODO: possibly remove?
     * 
     * This was originally added during conversion from FileDbAdapter to TFCouchDBService as 
     * a way to compare a serialised form definition with the original XML file.  It isn't being
     * used at the moment so we might want to remove it in the future.
     */
    private String xmlHash;
    
    GenericDocument(String type) {
        setType(type);
    }
    
    @JsonIgnore
    public static String generateTimestamp() 
    {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat formatter = (SimpleDateFormat)DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG);
        
        formatter.setTimeZone(TimeZone.getDefault());
        formatter.applyPattern(DATETIME);
        
        return formatter.format(calendar.getTime());       
    }
    
    public void setAuthoredBy(Integer author) {
        this.authoredBy = author;
    }    
    
    public Integer getAuthoredBy() {
        return authoredBy;
    }    
    
    public void setUpdatedBy(Integer author) {
        this.updatedBy = author;
    }    
    
    public Integer getUpdatedBy() {
        return updatedBy;
    }
    
    public void setDateCreated(String dateCreated) {
        this.dateCreated = dateCreated;
    }
    
    public String getDateCreated() {
        return dateCreated;
    }
    
    @JsonIgnore
    public Calendar getDateCreatedAsCalendar() {
        SimpleDateFormat sdf = new SimpleDateFormat(DATETIME);
        Calendar calendar = Calendar.getInstance();
        
        try {
            calendar.setTime(sdf.parse(dateCreated));
        } catch (ParseException e1) {
            Log.e(Collect.LOGTAG, t + "unable to parse dateCreated, returning a valid date anyway: " + e1.toString());            
        }
        
        return calendar;
    }
    
    public void setDateUpdated(String dateUpdated) {
        this.dateUpdated = dateUpdated;
    }
    
    public String getDateUpdated() {         
        return dateUpdated;
    }  
    
    @JsonIgnore
    public Calendar getDateUpdatedAsCalendar() {
        SimpleDateFormat sdf = new SimpleDateFormat(DATETIME);
        Calendar calendar = Calendar.getInstance();
        
        try {
            calendar.setTime(sdf.parse(dateUpdated));
        } catch (ParseException e1) {
            Log.e(Collect.LOGTAG, t + "unable to parse dateCreated, returning a valid date anyway: " + e1.toString());            
        }
        
        return calendar;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getType() {
        return type;
    }
    
    public void addInlineAttachment(Attachment a) {
        super.addInlineAttachment(a);
        
        /*
         *  Store a hash of the Base64 encoded data whenever we attach a file.  
         *  This will be used later on to determine the uniqueness of files.   
         */
        if (a.getId() == "xml") {
            if (a.getDataBase64().length() > 0) {
                setXmlHash(StringUtils.getMD5(a.getDataBase64()));                
            }
        }
    }

    public void setXmlHash(String hash)
    {
        this.xmlHash = hash;
    }

    public String getXmlHash()
    {
        return xmlHash;
    }

    public void setDocumentVersion(Integer documentVersion)
    {
        this.documentVersion = documentVersion;
    }

    public Integer getDocumentVersion()
    {
        return documentVersion;
    }
}
