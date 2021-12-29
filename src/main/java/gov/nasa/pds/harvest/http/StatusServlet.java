package gov.nasa.pds.harvest.http;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.jar.Attributes;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import gov.nasa.pds.harvest.cfg.Configuration;
import gov.nasa.pds.harvest.mq.MQClient;
import gov.nasa.pds.harvest.util.ManifestUtils;


/**
 * A servlet to report status of the Crawler server
 * @author karpenko
 */
@SuppressWarnings("serial")
public class StatusServlet extends HttpServlet 
{
    private Configuration cfg;
    private MQClient mqClient;
    
    private Gson gson;
    
    private String version;
    private String buildTime;
    
    /**
     * Model class for the status message 
     */
    @SuppressWarnings("unused")
    private static class Info
    {        
        public String application = "Harvest";
        public String version;
        public String buildTime;
        
        public String mqType;
        public String mqConnection;
        public String mqStatus;
        
        public String esUrl;
        public String esIndex;
        
        public String usedMemory;
        public String openFiles;
    }

    
    /**
     * Constructor
     * @param mqClient Messaging client
     */
    public StatusServlet(Configuration cfg, MQClient mqClient)
    {
        this.cfg = cfg;
        this.mqClient = mqClient;
        gson = new GsonBuilder().setPrettyPrinting().create();
        setVersioninfo();
    }
    

    /**
     * Cache version info
     */
    private void setVersioninfo()
    {
        version = this.getClass().getPackage().getImplementationVersion();
        Attributes attrs = ManifestUtils.getAttributes();
        if(attrs != null)
        {
            buildTime = attrs.getValue("Build-Time");
        }
    }

    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        Info info = new Info();
        
        // Version
        info.version = version;
        info.buildTime = buildTime;
        
        // MQ status
        info.mqType = mqClient.getType();
        info.mqConnection = mqClient.getConnectionInfo();
        info.mqStatus = mqClient.isConnected() ? "UP" : "DOWN";
        
        // Elasticsearch info
        info.esUrl = cfg.registryCfg.url;
        info.esIndex = cfg.registryCfg.indexName;
        
        // Memory
        int totalMem = (int)(Runtime.getRuntime().totalMemory() / 1_000_000);
        int freeMem = (int)(Runtime.getRuntime().freeMemory() / 1_000_000);
        info.usedMemory = (totalMem - freeMem) + " MB";
        
        // Open files (Unix only)
        info.openFiles = numOpenFiles();
        
        String jsonStr = gson.toJson(info);

        resp.setContentType("application/json");
        PrintWriter writer = resp.getWriter();
        writer.print(jsonStr);
    }

    
    private String numOpenFiles()
    {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        
        if(os instanceof com.sun.management.UnixOperatingSystemMXBean)
        {
            com.sun.management.UnixOperatingSystemMXBean unix = (com.sun.management.UnixOperatingSystemMXBean)os; 
            return String.valueOf(unix.getOpenFileDescriptorCount());
        }
        else
        {
            return "N/A";
        }
    }
}
