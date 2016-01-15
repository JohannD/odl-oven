/*
 * Copyright(c) Yoyodyne, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.oven.impl;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.OptimisticLockFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.RpcRegistration;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.impl.rev160113.Oven;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.impl.rev160113.OvenRuntimeMXBean;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.CookFoodInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.DisplayString;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.KitchenOutOfFoodBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.KitchenRestocked;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.KitchenRestockedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.OvenParams;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.OvenParams.OvenStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.OvenParamsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.OvenService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.RestockFoodInput;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

public class OvenProvider implements BindingAwareProvider, AutoCloseable, OvenService, OvenRuntimeMXBean, DataChangeListener  {

    private static final Logger LOG = LoggerFactory.getLogger(OvenProvider.class);

    public static final InstanceIdentifier<OvenParams> OVEN_IID = InstanceIdentifier
            .builder(OvenParams.class).build();
    static final DisplayString OVEN_MANUFACTURER = new DisplayString(
            "Opendaylight");
    static final DisplayString OVEN_MODEL = new DisplayString(
            "Model 1 BindingAware");
    private DataBroker db;
    private final ExecutorService executor;
    private volatile OvenStatus status = OvenStatus.Waiting;
    //private final AtomicInteger program = new AtomicInteger(0);
    private final AtomicInteger timer = new AtomicInteger(0);
    public AtomicInteger thermostat = new AtomicInteger(0);
    private final AtomicLong mealCooked = new AtomicLong(0);
    private final AtomicLong numberOfMealAvailable = new AtomicLong(0);
    private NotificationProviderService notificationProvider;
    private RpcRegistration<OvenService> rpcReg;
    private ListenerRegistration<DataChangeListener> dcReg;

    // The following holds the Future for the current make toast task.
    // This is used to cancel the current toast.
    private final AtomicReference<Future<?>> currentCookingMealTask = new AtomicReference<>();

    public OvenProvider() {
        executor = Executors.newFixedThreadPool(1);
    }

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("OvenProvider Session Initiated");
        this.db = session.getSALService(DataBroker.class);
        this.notificationProvider = session.getSALService(NotificationProviderService.class);

        // Register the RPC Service
        //rpcReg = session.addRpcImplementation(OvenService.class, this);

        // Register the DataChangeListener for Toaster's configuration subtree
        //dcReg = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION, OvenProvider.OVEN_IID, this, DataChangeScope.SUBTREE);

        if (db != null) {
            syncOvenParamWithDataStore(LogicalDatastoreType.OPERATIONAL,
                    OVEN_IID, buildOvenParams(status));
        } else {
            LOG.warn("No SALService DataBroker got.");
        }
        // syncOvenParamWithDataStore(LogicalDatastoreType.CONFIGURATION,OVEN_IID,buildOvenParams(status));
    }

    /**
     * This method is used to notify the OvenProvider when a data change
     * is made to the configuration.
     *
     * Effectively, the camera subtree node is modified through restconf
     * and the onDataChanged is triggered. We check if the changed dataObject is
     * from the camera subtree, if so we get the value of the thermostatFactor.
     *
     * If the brightnessFactor from the node is not null, then we change the thermostatFactor of
     * the OvenProvider with the subtree thermostatFactor.
     */
    @Override
    public void onDataChanged(
            AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
        final DataObject dataObject = change.getUpdatedSubtree();
        if (dataObject instanceof OvenParams) {
            final Integer thermostatFactor = ((OvenParams) dataObject).getThermostat();
            if (thermostatFactor!=null) {
                thermostat.set(thermostatFactor);
                LOG.info("@thermostatFactor:" +thermostatFactor);
            }
        }
    }

    /*
     * Default function to build a oven returns a OvenParams object using
     * the OvenParamsBuilder().build()
     */
    private OvenParams buildOvenParams(OvenStatus ovenStatus) {
        return new OvenParamsBuilder()
        .setOvenManufacturer(OVEN_MANUFACTURER)
        .setOvenModelNumber(OVEN_MODEL).setOvenStatus(ovenStatus)
        .build();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void syncOvenParamWithDataStore(final LogicalDatastoreType store,
            InstanceIdentifier iid, final DataObject oven) {
        final WriteTransaction transaction = db.newWriteOnlyTransaction();
        transaction.put(store, iid, oven);
        //Perform the tx.submit asynchronously
        Futures.addCallback(transaction.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(final Void result) {
                LOG.info("SyncStore {} with object {} succeeded", store, oven);
            }
            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("SyncStore {} with object {} failed", store, oven);
            }
        });
    }

    @Override
    public void close() throws Exception {
        executor.shutdown(); // part 2
        if (db != null) {
            final WriteTransaction transaction = db.newWriteOnlyTransaction();
            transaction.delete(LogicalDatastoreType.OPERATIONAL, OVEN_IID);
            Futures.addCallback(transaction.submit(),
                    new FutureCallback<Void>() {

                @Override
                public void onFailure(final Throwable t) {
                    LOG.error("Failed to delete oven" + t);
                }

                @Override
                public void onSuccess(final Void result) {
                    LOG.debug("Successfully deleted oven" + result);
                }
            });
        }
        if(rpcReg != null)
            rpcReg.close();

        if(dcReg != null)
            dcReg.close();

        LOG.info("OvenProvider Closed");
    }

    @Override
    public Long getMealCooked() {
        return mealCooked.get();
    }

    @Override
    public void clearMealCookedRpc() {
        LOG.info( "In clearMealCookedRpc" );
        mealCooked.set(0);

    }

    @Override
    public Future<RpcResult<Void>> cancelProgram() {
        final Future<?> current = currentCookingMealTask.getAndSet(null);
        if (current != null) {
            current.cancel(true);
        }
        // Always return success from the cancel toast call.
        return Futures.immediateFuture(RpcResultBuilder.<Void> success()
                .build());
    }

    @Override
    public Future<RpcResult<Void>> cookFood(CookFoodInput input) {
        LOG.info("In cookFood()");
        final SettableFuture<RpcResult<Void>> futureResult = SettableFuture
                .create();
        checkStatusandCookFood(input, futureResult, 2);
        return futureResult;
    }

    /**
     * Read the OvenStatus and, if currently Off, try to write the status to
     * On. If that succeeds, then we essentially have an exclusive lock and can
     * proceed to click the photo.
     *
     * @param input
     * @param futureResult
     * @param tries
     */
    private void checkStatusandCookFood(final CookFoodInput input,
            final SettableFuture<RpcResult<Void>> futureResult, final int tries) {
        /*
         * We create a ReadWriteTransaction by using the databroker. Then, we
         * read the status of the oven with getOvenStatus() using the
         * databroker again. Once we have the status, we analyze it and then
         * databroker submit function is called to effectively change the oven
         * status. This all affects the MD-SAL tree, more specifically the part
         * of the tree that contain the oven (the nodes).
         */
        LOG.info("In checkStatusandCookFood()");
        final ReadWriteTransaction tx = db.newReadWriteTransaction();
        final ListenableFuture<Optional<OvenParams>> readFuture = tx.read(
                LogicalDatastoreType.OPERATIONAL, OVEN_IID);

        final ListenableFuture<Void> commitFuture = Futures.transform(
                readFuture, new AsyncFunction<Optional<OvenParams>, Void>() {

                    @Override
                    public ListenableFuture<Void> apply(
                            Optional<OvenParams> ovenParamsData)
                                    throws Exception {
                        if (ovenParamsData.isPresent()) {
                            status = ovenParamsData.get().getOvenStatus();
                        } else {
                            throw new Exception(
                                    "Error reading OvenParams data from the store.");
                        }
                        LOG.info("Read oven status: {}", status);
                        if (status == OvenStatus.Waiting) {
                            //Check if numberOfMealAvailable is not 0, if yes Notify outOfStock
                            if(numberOfMealAvailable.get() == 0) {
                                LOG.info("No more meal availble to cook");
                                notificationProvider.publish(new KitchenOutOfFoodBuilder().build());
                                return Futures.immediateFailedCheckedFuture(
                                        new TransactionCommitFailedException("", clickNoMoreMealError()));
                            }

                            LOG.info("Setting Camera status to Preheating");
                            // We're not currently clicking photo - try to
                            // update the status to On
                            // to indicate we're going to click photo. This acts
                            // as a lock to prevent
                            // concurrent clicking.
                            tx.put(LogicalDatastoreType.OPERATIONAL,
                                    OVEN_IID,
                                    buildOvenParams(OvenStatus.Preheating));
                            return tx.submit();
                        }
                        LOG.info("The oven is actually on use, cancel actual program before.");
                        // Return an error since we are already clicking photo.
                        // This will get
                        // propagated to the commitFuture below which will
                        // interpret the null
                        // TransactionStatus in the RpcResult as an error
                        // condition.
                        return Futures
                                .immediateFailedCheckedFuture(new TransactionCommitFailedException(
                                        "", clickOvenInUseError()));
                    }

                    private RpcError clickNoMoreMealError() {
                        return RpcResultBuilder.newError( ErrorType.APPLICATION, "resource-denied",
                                "No more photos available for clicking", "out-of-stock", null, null );
                    }
                });
        Futures.addCallback(commitFuture, new FutureCallback<Void>() {

            @Override
            public void onFailure(Throwable t) {
                if (t instanceof OptimisticLockFailedException) {
                    // Another thread is likely trying to click a photo
                    // simultaneously and updated the
                    // status before us. Try reading the status again - if
                    // another click-photo is
                    // now in progress, we should get OvenStatus.Off and fail.
                    if ((tries - 1) > 0) {
                        LOG.info("Got OptimisticLockFailedException - trying again");
                        checkStatusandCookFood(input, futureResult, tries - 1);
                    } else {
                        futureResult.set(RpcResultBuilder
                                .<Void> failed()
                                .withError(ErrorType.APPLICATION,
                                        t.getMessage()).build());
                    }
                } else {
                    LOG.info("Failed to commit Oven status", t);
                    // Probably already clicking a photo.
                    futureResult.set(RpcResultBuilder
                            .<Void> failed()
                            .withRpcErrors(
                                    ((TransactionCommitFailedException) t)
                                    .getErrorList()).build());
                }
            }

            @Override
            public void onSuccess(Void result) {
                // OK to click a photo
                currentCookingMealTask.set(executor.submit(new CookMealTask(
                        input, futureResult)));

            }

        });
    }

    private RpcError clickOvenInUseError() {
        return RpcResultBuilder.newWarning(ErrorType.APPLICATION, "in use",
                "Oven is busy (in-use)", null, null, null);
    }

    private class CookMealTask implements Callable<Void> {
        final CookFoodInput mealRequest;
        final SettableFuture<RpcResult<Void>> futureResult;

        public CookMealTask(CookFoodInput mealRequest,
                SettableFuture<RpcResult<Void>> futureResult) {
            this.mealRequest = mealRequest;
            this.futureResult = futureResult;
        }

        @Override
        public Void call() throws Exception {
            // click photo sleeps for n seconds for every 1 unit change of
            // exposure level
            LOG.info("Inside ClickPhotoTask's call() method");
            LOG.info("Temperature is:" + mealRequest.getTemperature());
            LOG.info("Timer is:" + mealRequest.getTimer());
            if(mealRequest.getProgram() != null)
                LOG.info("PhotoType is:" +mealRequest.getProgram().getName());
            try {
                final int timer = OvenProvider.this.timer.get();

                //Get the brightness constant if present
                int bF=1;
                if(OvenProvider.this.thermostat!=null)
                    bF = OvenProvider.this.thermostat.get();
                Thread.sleep(Math.abs(bF*(timer + mealRequest.getTimer())));
            } catch (final InterruptedException e) {
                LOG.info("Interrupted while clicking photo");
            }
            numberOfMealAvailable.getAndDecrement();
            // need to redo this even though it is already handled in checkStatusAndCLickPhoto before it gets here.
            //since if the first time getAndDecrement() on numberOfPhotos gets called and is 0 then publish Notif
            if(numberOfMealAvailable.get()==0) {
                LOG.info("Oven out of Memory to click photos!!!!");
                notificationProvider.publish(new KitchenOutOfFoodBuilder().build());
            }
            mealCooked.incrementAndGet();
            syncOvenParamWithDataStore(LogicalDatastoreType.OPERATIONAL,
                    OVEN_IID, buildOvenParams(OvenStatus.Preheating));

            // Set the Oven status back to off - this essentially releases the
            // photo clicking lock.
            // We can't clear the current click photo task nor set the Future
            // result until the
            // update has been committed so we pass a callback to be notified on
            // completion.

            //To-Do:  Instead of Sleep here, add a callback to syncOvenParamWithDataStore
            //to return a boolean to let you know about the completion of cookFood,
            //if true then only reset the OvenStatus to off
            Thread.sleep(10);
            setOvenStatusOff(new Function<Boolean, Void>() {
                @Override
                public Void apply(Boolean result) {
                    currentCookingMealTask.set(null);
                    LOG.info("Oven Ready @setOvenStatusOff.apply()");
                    futureResult.set(RpcResultBuilder.<Void> success().build());
                    return null;
                }
            });
            return null;
        }

        private void setOvenStatusOff(
                final Function<Boolean, Void> resultCallback) {
            final WriteTransaction tx = db.newWriteOnlyTransaction();
            tx.put(LogicalDatastoreType.OPERATIONAL, OVEN_IID,
                    buildOvenParams(OvenStatus.Waiting));

            Futures.addCallback(tx.submit(), new FutureCallback<Void>() {

                @Override
                public void onFailure(Throwable t) {
                    LOG.error("Failed to reset the Oven Status to Waiting");
                    notifyCallback(false);
                }

                @Override
                public void onSuccess(Void arg0) {
                    LOG.info("Reset Oven Status to Waiting");
                    notifyCallback(true);
                }

                private void notifyCallback(boolean b) {
                    if (resultCallback != null) {
                        resultCallback.apply(b);
                    }
                }
            });
        }
    }

    @Override
    public Future<RpcResult<Void>> listProgram() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Future<RpcResult<Void>> restockFood(RestockFoodInput input) {
        LOG.info("currentNumberOfMeal:" +numberOfMealAvailable.get()+ "  RestockFoodInput:" +input);
        final Long currentNumberOfMeal = numberOfMealAvailable.get();
        numberOfMealAvailable.set(currentNumberOfMeal+input.getAmountOfFoodToStock());
        //Store numberOfPhotosAvailable in OP-DataStore TO-DO RASHMI
        if(input.getAmountOfFoodToStock()>0) {
            final KitchenRestocked kitchenRestockedNotif= new KitchenRestockedBuilder().setNumberOfMeal(input.getAmountOfFoodToStock()).build();
            notificationProvider.publish(kitchenRestockedNotif);
        }

        return Futures.immediateFuture( RpcResultBuilder.<Void> success().build() );
    }

}
