package org.pentaho.reporting.ui.datasources.bigdata;

import java.lang.reflect.Constructor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pentaho.di.core.exception.KettlePluginException;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.plugins.DataFactoryPluginType;
import org.pentaho.di.core.plugins.PluginInterface;
import org.pentaho.di.core.plugins.PluginRegistry;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.datafactory.DynamicDatasource;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.ui.trans.step.BaseStepGenericXulDialog;
import org.pentaho.reporting.engine.classic.core.DataFactory;
import org.pentaho.reporting.engine.classic.core.ParameterMapping;
import org.pentaho.reporting.engine.classic.core.ReportDataFactoryException;
import org.pentaho.reporting.engine.classic.core.designtime.DataFactoryChangeRecorder;
import org.pentaho.reporting.engine.classic.core.designtime.DataSourcePlugin;
import org.pentaho.reporting.engine.classic.core.designtime.DesignTimeContext;
import org.pentaho.reporting.engine.classic.core.metadata.DataFactoryMetaData;
import org.pentaho.reporting.engine.classic.core.metadata.DataFactoryRegistry;
import org.pentaho.reporting.engine.classic.extensions.datasources.bigdata.BigDataDataFactory;
import org.pentaho.reporting.engine.classic.extensions.datasources.bigdata.BigDataHelper;
import org.pentaho.reporting.engine.classic.extensions.datasources.bigdata.BigDataQueryTransformationProducer;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class BigDataDataSourcePlugin implements DataSourcePlugin
{
  private static final Log logger = LogFactory.getLog(BigDataDataSourcePlugin.class);
  private String id;

  public BigDataDataSourcePlugin() throws ReportDataFactoryException
  {
    id = "MongoDB"; // TODO: Allow other datasources.
  }

  private DynamicDatasource loadDataSource() throws KettlePluginException
  {
    // Load the main plugin class, as it holds all of the relevant details we need to realize this
    // datasource
    final PluginInterface plugin = PluginRegistry.getInstance().getPlugin(DataFactoryPluginType.class, id);
    return (DynamicDatasource) PluginRegistry.getInstance().loadClass(plugin);
  }


  public boolean canHandle(final DataFactory dataFactory)
  {
    return dataFactory instanceof BigDataDataFactory;
  }

  private TransMeta loadTransformation(final DynamicDatasource cls) throws KettlePluginException, KettleXMLException
  {
    final Document document = BigDataHelper.loadDocumentFromPlugin(cls);
    final Node node = XMLHandler.getSubNode(document, TransMeta.XML_TAG);
    final TransMeta meta = new TransMeta();
    meta.loadXML(node, null, true, null, null);
    return meta;
  }

  private BaseStepGenericXulDialog createDialog(final DynamicDatasource cls,
                                                final StepMeta step,
                                                final DesignTimeContext context)
  {
    // Render datasource specific dialog for editing step details...
    try
    {
      final Class<? extends BaseStepGenericXulDialog> dialog = (Class<? extends BaseStepGenericXulDialog>)
          Class.forName(cls.getDialogClass(), true, cls.getClass().getClassLoader());

      final Constructor<? extends BaseStepGenericXulDialog> constructor =
          dialog.getDeclaredConstructor(Object.class, BaseStepMeta.class, TransMeta.class, String.class);
      return constructor.newInstance(context.getParentWindow(), step.getStepMetaInterface(),
          step.getParentTransMeta(), cls.getStepName());

    }
    catch (Exception e)
    {

      logger.error("Critical error attempting to dynamically create dialog. This datasource will not be available.", e);
      return null;

    }
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public DataFactory performEdit(final DesignTimeContext context,
                                 final DataFactory input,
                                 final String queryName,
                                 final DataFactoryChangeRecorder changeRecorder)
  {

    final BigDataDataFactory bigDataDataFactory = (BigDataDataFactory) input;
    try
    {
      final DynamicDatasource cls = loadDataSource();
      TransMeta transMeta;
      if (bigDataDataFactory == null)
      {
        transMeta = loadTransformation(cls);
      }
      else
      {
        final BigDataQueryTransformationProducer query = bigDataDataFactory.getQuery(id);
        if (query == null)
        {
          transMeta = loadTransformation(cls);
        }
        else
        {
          final Document document = BigDataHelper.loadDocumentFromBytes(query.getBigDataTransformationRaw());
          final Node node = XMLHandler.getSubNode(document, TransMeta.XML_TAG);
          transMeta = new TransMeta();
          transMeta.loadXML(node, null, true, null, null);
        }
      }

      final StepMeta step = transMeta.findStep(cls.getStepName());

      // dialog OK button clicked ...
      final BaseStepGenericXulDialog dlg = createDialog(cls, step, context);
      if (dlg.open() != null)
      {
        transMeta.addOrReplaceStep(step);
        final byte[] rawData = transMeta.getXML().getBytes("UTF8");
        final BigDataDataFactory retval = new BigDataDataFactory();

        // todo: No parameter definitions here!
        retval.setQuery(id, new BigDataQueryTransformationProducer(new String[0], new ParameterMapping[0], 
                                                                             id, cls.getStepName(), rawData));
        return retval;
      }

      return input;
    }
    catch (Exception e)
    {
      context.error(e);
      return input;
    }
  }

  public DataFactoryMetaData getMetaData()
  {
    return DataFactoryRegistry.getInstance().getMetaData(BigDataDataFactory.class.getName());
  }
}
