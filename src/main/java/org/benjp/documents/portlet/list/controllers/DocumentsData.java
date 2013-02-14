package org.benjp.documents.portlet.list.controllers;


import juzu.SessionScoped;
import org.benjp.documents.portlet.list.bean.File;
import org.benjp.documents.portlet.list.bean.VersionBean;
import org.benjp.documents.portlet.list.comparator.*;
import org.benjp.documents.portlet.list.controllers.validator.NameValidator;
import org.exoplatform.portal.application.PortalRequestContext;
import org.exoplatform.portal.webui.util.Util;
import org.exoplatform.services.cms.folksonomy.NewFolksonomyService;
import org.exoplatform.services.cms.link.LinkManager;
import org.exoplatform.services.jcr.RepositoryService;
import org.exoplatform.services.jcr.core.nodetype.ExtendedNodeTypeManager;
import org.exoplatform.services.jcr.core.nodetype.NodeTypeValue;
import org.exoplatform.services.jcr.core.nodetype.PropertyDefinitionValue;
import org.exoplatform.services.jcr.ext.common.SessionProvider;
import org.exoplatform.services.jcr.ext.hierarchy.NodeHierarchyCreator;

import javax.inject.Inject;
import javax.inject.Named;
import javax.jcr.*;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.NodeType;
import javax.jcr.version.OnParentVersionAction;
import javax.jcr.version.VersionIterator;
import javax.servlet.http.HttpServletRequest;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

@Named("documentsData")
@SessionScoped
public class DocumentsData {

  RepositoryService repositoryService_;

  NewFolksonomyService newFolksonomyService_;

  NodeHierarchyCreator nodeHierarchyCreator_;

  LinkManager linkManager_;

  private static final String META_NODETYPE = "adn:meta";
  private static final String SIZE_PROPERTY = "adn:size";
  private static final String TIMESTAMP_PROPERTY = "adn:timestamp";


  public static final String TYPE_DOCUMENT="Documents";
  public static final String TYPE_IMAGE="Pictures";

  @Inject
  public DocumentsData(RepositoryService repositoryService, NodeHierarchyCreator nodeHierarchyCreator, NewFolksonomyService newFolksonomyService, LinkManager linkManager)
  {
    repositoryService_ = repositoryService;
    nodeHierarchyCreator_= nodeHierarchyCreator;
    newFolksonomyService_ = newFolksonomyService;
    linkManager_ = linkManager;
  }

  protected void initNodetypes()
  {
    SessionProvider sessionProvider = SessionProvider.createSystemProvider();
    try
    {
      //get info
      Session session = sessionProvider.getSession("collaboration", repositoryService_.getCurrentRepository());

      NamespaceRegistry namespaceRegistry = session.getWorkspace().getNamespaceRegistry();
      try
      {
        String uri = namespaceRegistry.getURI("adn");
      }
      catch (NamespaceException ne)
      {
        namespaceRegistry.registerNamespace("adn", "http://www.exoplatform.com/jcr/adn/1.0");
      }

      ExtendedNodeTypeManager nodeTypeManager = (ExtendedNodeTypeManager) session.getWorkspace().getNodeTypeManager();
      try {
        NodeType ntMeta = nodeTypeManager.getNodeType(META_NODETYPE);

      } catch (NoSuchNodeTypeException nsne)
      {
        NodeTypeValue adnMeta = new NodeTypeValue();
        adnMeta.setName(META_NODETYPE);
        adnMeta.setMixin(true);

        PropertyDefinitionValue sizeProperty = new PropertyDefinitionValue();
        sizeProperty.setMultiple(false);
        sizeProperty.setAutoCreate(false);
        sizeProperty.setName(SIZE_PROPERTY);
        sizeProperty.setReadOnly(false);
        sizeProperty.setRequiredType(PropertyType.LONG);
        sizeProperty.setOnVersion(OnParentVersionAction.IGNORE);

        PropertyDefinitionValue tsProperty = new PropertyDefinitionValue();
        tsProperty.setMultiple(false);
        tsProperty.setAutoCreate(false);
        tsProperty.setName(TIMESTAMP_PROPERTY);
        tsProperty.setReadOnly(false);
        tsProperty.setRequiredType(PropertyType.LONG);
        tsProperty.setOnVersion(OnParentVersionAction.IGNORE);

        List<PropertyDefinitionValue> props = new ArrayList<PropertyDefinitionValue>();
        props.add(sizeProperty);
        props.add(tsProperty);

        adnMeta.setDeclaredPropertyDefinitionValues(props);

        nodeTypeManager.registerNodeType(adnMeta, ExtendedNodeTypeManager.REPLACE_IF_EXISTS);
      }

    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    finally
    {
      sessionProvider.close();
    }


  }

  protected boolean restoreVersion(String uuid, String name)
  {
    SessionProvider sessionProvider = SessionProvider.createSystemProvider();
    try
    {
      //get info
      Session session = sessionProvider.getSession("collaboration", repositoryService_.getCurrentRepository());

      Node node = session.getNodeByUUID(uuid);
      node.restore(name, true);
      return true;
    }
    catch (Exception e)
    {
      System.out.println("JCR::\n" + e.getMessage());
    }
    finally
    {
      sessionProvider.close();
    }
    return false;
  }

  protected File getNode(String id)
  {

    SessionProvider sessionProvider = SessionProvider.createSystemProvider();
    try
    {
      //get info
      Session session = sessionProvider.getSession("collaboration", repositoryService_.getCurrentRepository());

      Node node = getNodeById(id, session);

      String space = getSpaceName();

      File file = getFileFromNode(node, space, true);

      return file;
    }
    catch (Exception e)
    {
      System.out.println("JCR::\n" + e.getMessage());
    }
    finally
    {
      sessionProvider.close();
    }


    return null;
  }

  protected boolean createNodeIfNotExist(String filter, String name)
  {
    //filter = filter+"/"+name;
    SessionProvider sessionProvider = SessionProvider.createSystemProvider();
    try
    {
      //get info
      Session session = sessionProvider.getSession("collaboration", repositoryService_.getCurrentRepository());


      Node rootNode = session.getRootNode();
      String space = getSpaceName();
      String path = (space!=null)?getSpacePath(space):getUserPrivatePath();

      if (space != null && filter.startsWith("Folksonomy/"))
      {
        filter = filter.replace("Folksonomy/", "ApplicationData/Tags/");
      }

      if (!rootNode.hasNode(path+"/"+filter+"/"+name)) {
        Node parentNode = rootNode.getNode(path+"/"+filter);
        parentNode.addNode(name, "nt:folder");
        parentNode.save();
        session.save();
      }
    } catch (Exception e)
    {
      return false;
    }
    finally {
      sessionProvider.close();
    }
    return true;

  }


  protected List<File> getNodes(String filter, String order, String by)
  {
    SessionProvider sessionProvider = SessionProvider.createSystemProvider();
    try
    {
      //get info
      Session session = sessionProvider.getSession("collaboration", repositoryService_.getCurrentRepository());


      Node rootNode = session.getRootNode();
      String space = getSpaceName();
      String path = (space!=null)?getSpacePath(space):getUserPrivatePath();

      if (space != null && filter.startsWith("Folksonomy/"))
      {
        filter = filter.replace("Folksonomy/", "ApplicationData/Tags/");
      }

      if (!rootNode.hasNode(path+"/"+filter)) {
        Node parentNode = rootNode.getNode(path);
        parentNode.addNode(filter, "nt:folder");
        parentNode.save();
      }

      Node docNode = rootNode.getNode(path+"/"+filter);

      NodeIterator nodes = docNode.getNodes();
      List<File> files = new ArrayList<File>();
      while (nodes.hasNext())
      {
        Node node = nodes.nextNode();
        node = getTargetNode(node);
        if (isAcceptedFile(node.getName()) || node.isNodeType("nt:folder"))
        {
          File file = getFileFromNode(node, space, false);
          files.add(file);
        }
      }
      session.save();

      if ("asc".equals(order))
      {
        if ("date".equals(by))
          Collections.sort(files, new FileDateComparator());
        else if ("size".equals(by))
          Collections.sort(files, new FileSizeComparator());
        else
          Collections.sort(files, new FileNameComparator());
      }
      else
      {
        if ("date".equals(by))
          Collections.sort(files, new FileDateReverseComparator());
        else if ("size".equals(by))
          Collections.sort(files, new FileSizeReverseComparator());
        else
          Collections.sort(files, new FileNameReverseComparator());
      }

      return files;

    }
    catch (Exception e)
    {
      System.out.println("JCR::\n" + e.getMessage());
    }
    finally
    {
      sessionProvider.close();
    }
    return null;
  }

  private File getFileFromNode(Node node, String space, boolean full) throws Exception {
    File file = new File();
    //set name
    file.setName(node.getName());
    //set uuid
    if (node.isNodeType("mix:referenceable")) file.setUuid(node.getUUID());
    //hasMeta?
    if (!node.isNodeType(META_NODETYPE))
    {
      node.addMixin(META_NODETYPE);
      node.save();
    }

    // set created date
    Calendar date = node.getProperty("exo:dateModified").getDate();
    file.setCreatedDate(date);
    if (!node.hasProperty(TIMESTAMP_PROPERTY))
    {
      node.setProperty(TIMESTAMP_PROPERTY, date.getTimeInMillis());
      node.save();
    }

    // is file or folder
    if (node.isNodeType("nt:folder")) file.setAsFolder();
    //set file size
    Long size;
    if (node.hasProperty(SIZE_PROPERTY))
    {
      size = node.getProperty(SIZE_PROPERTY).getLong();
    }
    else
    {
      if (node.hasNode("jcr:content"))
      {
        Node contentNode = node.getNode("jcr:content");
        size = contentNode.getProperty("jcr:data").getLength();
        node.setProperty(SIZE_PROPERTY, size);
        node.save();
      }
      else
      {
        size = node.getNodes().getSize();
        node.setProperty(SIZE_PROPERTY, size);
        node.save();
      }
    }
    String fileSize = calculateFileSize(size);
    if (file.isFile())
      file.setSize(fileSize);
    else
      file.setSize(""+size);
    file.setSizeValue(size);


    // set versions
    String sversion = "";
    if (node.isNodeType("mix:versionable"))
    {
      long ivers = node.getVersionHistory().getAllVersions().getSize() - 1;
      sversion = (ivers<=0)?"":"V"+ivers;
    }
    file.setVersion(sversion);
    // set path
    file.setPath(node.getPath());
    // set public url
    HttpServletRequest request = Util.getPortalRequestContext().getRequest();
    String baseURI = request.getScheme() + "://" + request.getServerName() + ":"
            + String.format("%s", request.getServerPort());

    String url = baseURI+ "/documents/file/" +Util.getPortalRequestContext().getRemoteUser()+"/"+file.getUuid()+"/"+file.getName();
    file.setPublicUrl(url);

    //set tags
    List<Node> tags;
    if (space!=null)
    {
      tags = newFolksonomyService_.getLinkedTagsOfDocumentByScope(NewFolksonomyService.GROUP,
              "/spaces/"+space,
              node, "collaboration");
    }
    else
    {
      tags = newFolksonomyService_.getLinkedTagsOfDocumentByScope(NewFolksonomyService.PRIVATE,
              Util.getPortalRequestContext().getRemoteUser(),
              node, "collaboration");
    }


    List<String> stags = new ArrayList<String>();
    if (tags!=null && tags.size()>0)
    {

      for (Node tag:tags)
      {
        stags.add(tag.getName());
      }
    }
    file.setTags(stags);

    if (full)
    {

      if (node.isNodeType("mix:versionable"))
      {
        VersionIterator iterator = node.getVersionHistory().getAllVersions();
        List<VersionBean> listVersions = new ArrayList<VersionBean>();
        while (iterator.hasNext())
        {
          javax.jcr.version.Version version = iterator.nextVersion();
          //System.out.println("VERSION :: " + version.getName() + " :: " + version.getCreated().getTimeInMillis());
          VersionBean versionBean = new VersionBean();
          versionBean.setName(version.getName());
          versionBean.setCreatedDate(version.getCreated().getTime());
          listVersions.add(versionBean);

        }
        file.setVersions(listVersions);
      }
    }

    return file;
  }

  private Node getTargetNode(Node showingNode) throws Exception {
    Node targetNode = null;
    if (linkManager_.isLink(showingNode)) {
      try {
        targetNode = linkManager_.getTarget(showingNode);
      } catch (ItemNotFoundException e) {
        targetNode = showingNode;
      }
    } else {
      targetNode = showingNode;
    }
    return targetNode;
  }


  protected void deleteFile(String id) throws Exception
  {
    SessionProvider sessionProvider = SessionProvider.createSystemProvider();
    try
    {
      Session session = sessionProvider.getSession("collaboration", repositoryService_.getCurrentRepository());

      Node node = getNodeById(id, session);

      node.remove();
      session.save();
    }
    finally
    {
      sessionProvider.close();
    }

  }

  protected void renameFile(String id, String name) throws Exception
  {
    NameValidator.validateName(name);
    SessionProvider sessionProvider = SessionProvider.createSystemProvider();
    try
    {
      Session session = sessionProvider.getSession("collaboration", repositoryService_.getCurrentRepository());

      Node node = getNodeById(id, session);
      String extension = (!id.contains("/"))?node.getName().substring(node.getName().lastIndexOf(".")):"";

      StringBuilder newPath = new StringBuilder(node.getParent().getPath()).append('/')
              .append(name).append(extension);
      session.move(node.getPath(), newPath.toString());
      session.save();
    }
    finally
    {
      sessionProvider.close();
    }

  }

  private Node getNodeById(String id, Session session) throws Exception
  {
    Node node = null;
    if (!id.contains("/"))
    {
      node = session.getNodeByUUID(id);
    }
    else
    {
      Node rootNode = session.getRootNode();
      String path = (id.startsWith("/"))?id.substring(1):id;
      node = rootNode.getNode(path);
    }

    return node;
  }

  protected void editTags(String uuid, String tags) throws Exception
  {
    String tagsPath = "/"+getUserPrivatePath()+"/Folksonomy/";

    SessionProvider sessionProvider = SessionProvider.createSystemProvider();
    try
    {
      Session session = sessionProvider.getSession("collaboration", repositoryService_.getCurrentRepository());
      Node node = session.getNodeByUUID(uuid);

      /**
       * TODO : Optimize this, remove existing if not in new list, add only if new
       * */
      List<Node> tagsNodes;
      String space = getSpaceName();
      if (space!=null)
      {
        tagsNodes = newFolksonomyService_.getLinkedTagsOfDocumentByScope(NewFolksonomyService.GROUP,
                "/spaces/"+space,
                node, "collaboration");
      }
      else
      {
        tagsNodes = newFolksonomyService_.getLinkedTagsOfDocumentByScope(NewFolksonomyService.PRIVATE,
                Util.getPortalRequestContext().getRemoteUser(),
                node, "collaboration");
      }
      if (tagsNodes!=null && tagsNodes.size()>0)
      {
        for (Node tag:tagsNodes)
        {
          newFolksonomyService_.removeTagOfDocument(tagsPath+tag.getName(), node, "collaboration");
        }
      }

      if (tags!=null && !"".equals(tags))
      {
        String[] atags = tags.replaceAll(" ", "").toLowerCase().split(",");
        if (space!=null)
        {
          newFolksonomyService_.addGroupsTag(atags, node, "collaboration", new String[]{"/spaces/"+space});
        }
        else
        {
          newFolksonomyService_.addPrivateTag(atags, node, "collaboration", Util.getPortalRequestContext().getRemoteUser());
        }

        session.save();
      }
    }
    finally
    {
      sessionProvider.close();
    }

  }

  private String getUserPrivatePath()
  {
    String userName = Util.getPortalRequestContext().getRemoteUser();

    SessionProvider sessionProvider = SessionProvider.createSystemProvider();
    try
    {
      Node userNode = nodeHierarchyCreator_.getUserNode(sessionProvider, userName);
      return userNode.getPath().substring(1)+"/Private";
    }
    catch (Exception e)
    {
      System.out.println("JCR::" + e.getMessage());
    }
    finally
    {
      sessionProvider.close();
    }

    return null;
  }

  private static String getSpacePath(String space)
  {
    return "Groups/spaces/"+space;
  }

  public static String calculateFileSize(double fileLengthLong) {
    int fileLengthDigitCount = Double.toString(fileLengthLong).length();
    double fileSizeKB = 0.0;
    String howBig = "";
    if (fileLengthDigitCount < 5) {
      fileSizeKB = Math.abs(fileLengthLong);
      howBig = "Byte(s)";
    } else if (fileLengthDigitCount >= 5 && fileLengthDigitCount <= 6) {
      fileSizeKB = Math.abs((fileLengthLong / 1024));
      howBig = "KB";
    } else if (fileLengthDigitCount >= 7 && fileLengthDigitCount <= 9) {
      fileSizeKB = Math.abs(fileLengthLong / (1024 * 1024));
      howBig = "MB";
    } else if (fileLengthDigitCount > 9) {
      fileSizeKB = Math.abs((fileLengthLong / (1024 * 1024 * 1024)));
      howBig = "GB";
    }
    String finalResult = roundTwoDecimals(fileSizeKB);
    return finalResult + " " + howBig;
  }

  public String getSpaceName()
  {
    PortalRequestContext portalRequestContext = PortalRequestContext.getCurrentInstance();
    // /portal/g/:spaces:espace_rh/espace_rh/DocumentsListApplication
    String uri = portalRequestContext.getRequest().getRequestURI();

    // /Groups/spaces/espace_rh/Documents

    int io = uri.indexOf(":spaces:");
    if (io==-1) return null;

    String suri = uri.substring(io+8);
    suri = suri.substring(0, suri.indexOf("/"));

    return suri;
  }




  private static String roundTwoDecimals(double d) {
    DecimalFormat twoDForm = new DecimalFormat("#.##");
    return twoDForm.format(d);
  }

  private boolean isAcceptedFile(String filename)
  {
    if (filename.endsWith(".jpg") || filename.endsWith(".png") || filename.endsWith(".pdf")
            || filename.endsWith(".ppt") || filename.endsWith(".xls") || filename.endsWith(".doc")
            || filename.endsWith(".pptx") || filename.endsWith(".xlsx") || filename.endsWith(".docx")
            || filename.endsWith(".odt") || filename.endsWith(".ods") || filename.endsWith(".odp"))
      return true;
    return false;
  }


}
