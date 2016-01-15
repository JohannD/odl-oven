package org.opendaylight.oven.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.OvenParams;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.OvenParams.OvenStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.OvenParamsBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OvenDataChangeListener implements DataChangeListener, AutoCloseable {
  //Through YANG file I added a configuration parameter called brightnessFactor,
    //so the Provider class needs to listen to changes made to this config param.
    //This class is invoked my the OvenModule class from onSessionInitiated()
    // i.e. OvenProvider/OvenDataChangeListener must implement DataChangeListener's onDataChanged() do define the behavior.
    // Also, modify code for any operation that needs to use this parameter, i.e. ClickPhotoTask.
    // Finally, register our listener to DataProviderService
    //CHECK!!! If you need to update default-config.xml with binding for datachangelistener service - mostly NO

    private static final Logger LOG = LoggerFactory
            .getLogger(OvenDataChangeListener.class);
    private final DataBroker db;

    public OvenDataChangeListener(DataBroker dataBroker) {
        this.db=dataBroker;
        //Registering the listener for changes made to CONFIG datastore
        final ListenerRegistration<DataChangeListener> dataChangeListenerRegistration =
                db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION, OvenProvider.OVEN_IID, this, DataChangeScope.SUBTREE);
        LOG.info("OvenDataChangeListener Registered");
    }

    /**
     * This method is used to notify the OvenProvider when a data change
     * is made to the configuration.
     *
     * Effectively, the camera subtree node is modified through restconf
     * and the onDataChanged is triggered. We check if the changed dataObject is
     * from the camera subtree, if so we get the value of the brightnessFactor.
     *
     * If the brightnessFactor from the node is not null, then we change the brightnessFactor of
     * the OvenProvider with the subtree brightnessFactor.
     */
    @Override
    public void onDataChanged(
            AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
        //Note 1: DataObject will contain only the config parameter value from the RESTConf for config i.e. brightnessFactor only
        //Note 2: Transaction.put updates the entire tree, if a node is null it doesn't add it to the tree though
        //Note 3: There is no implementation needed to do a GET request from RESTConf for Operational & Config Datastore.
        final DataObject dataObject = change.getUpdatedSubtree();
        if (dataObject instanceof OvenParams) {
            final Integer thermostatFactor = ((OvenParams) dataObject).getThermostat();
            if (thermostatFactor!=null) {
                //Rebuild OvenParams obj with current values plus set the brightnessFactor & store in Operational DataStore
                LOG.info("@thermostatFactor:" +thermostatFactor);//+ " @cameraStatus:" +((OvenParams) change.getOriginalSubtree()).getOvenStatus());
                final OvenParams ovenParams = new OvenParamsBuilder()
                        .setOvenManufacturer(OvenProvider.OVEN_MANUFACTURER)
                        .setOvenModelNumber(OvenProvider.OVEN_MODEL)
                        .setOvenStatus(((OvenParams) change.getOriginalSubtree()).getOvenStatus())
                        .setThermostat(thermostatFactor)
                        .build();
                final WriteTransaction transaction = db.newWriteOnlyTransaction();
                transaction.put(LogicalDatastoreType.OPERATIONAL, OvenProvider.OVEN_IID, ovenParams);
                transaction.submit();
            }
        }
    }


    @Override
    public void close() throws Exception {
        LOG.info("OvenDataChangeListener closed");

    }
}
