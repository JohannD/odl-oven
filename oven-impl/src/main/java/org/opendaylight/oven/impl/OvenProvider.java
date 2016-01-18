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
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.RpcRegistration;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
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
    public static final DisplayString OVEN_MANUFACTURER = new DisplayString("Miele");
    public static final DisplayString OVEN_MODEL = new DisplayString("PureLine M Touch");


    private NotificationProviderService notificationProvider;
    private RpcRegistration<OvenService> rpcReg;
    private ListenerRegistration<DataChangeListener> dcReg;
    private DataBroker db;

    private final ExecutorService executor;

    private volatile OvenStatus status = OvenStatus.Waiting;
    //private final AtomicInteger program = new AtomicInteger(0);
    private final AtomicInteger timer = new AtomicInteger(1);
    public AtomicInteger thermostat = new AtomicInteger(1);
    private final AtomicLong mealCooked = new AtomicLong(0);
    private final AtomicLong numberOfMealAvailable = new AtomicLong(100);

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
     * If the thermostatFactor from the node is not null, then we change the thermostatFactor of
     * the OvenProvider with the subtree thermostatFactor.
     */
    @Override
    public void onDataChanged( AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change ) {
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
            Futures.addCallback(transaction.submit(), new FutureCallback<Void>() {
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

        LOG.info("OvenProvider closed");
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
        LOG.info("cancelProgram");
        final Future<?> current = currentCookingMealTask.getAndSet(null);
        if (current != null) {
            current.cancel(true);
        }
        // Always return success from the cancel toast call.
        return Futures.immediateFuture(RpcResultBuilder.<Void> success().build());
    }

    @Override
    public Future<RpcResult<Void>> cookFood(CookFoodInput input) {
        //LOG.info("In cookFood()");
        LOG.info("cookFood: {}", input);
        final SettableFuture<RpcResult<Void>> futureResult = SettableFuture.create();
        checkStatusAndCookFood(input, futureResult, 2);
        LOG.info("cookFood returning...");
        return futureResult;
    }

    /**
     * Read the OvenStatus and, if currently Waiting, try to write the status to Preheating. 
     * If that succeeds, then we can proceed to cook the food. 
     *
     * @param input
     * @param futureResult
     * @param tries
     */
    private void checkStatusAndCookFood(final CookFoodInput input,
            final SettableFuture<RpcResult<Void>> futureResult,
            final int tries) {
        /*
         * We create a ReadWriteTransaction by using the databroker. Then, we
         * read the status of the oven with getOvenStatus() using the
         * databroker again. Once we have the status, we analyze it and then
         * databroker submit function is called to effectively change the oven
         * status. This all affects the MD-SAL tree, more specifically the part
         * of the tree that contain the oven (the nodes).
         */
        LOG.info("In checkStatusAndCookFood()");
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
                                "Error reading OvenParams.status data from the store.");
                    }
                    LOG.info("Read oven status: {}", status);

                    if (status == OvenStatus.Waiting) {
                        //Check if numberOfMealAvailable is not 0, if yes Notify outOfStock
                        if(numberOfMealAvailable.get() == 0) {
                            LOG.info("No more meal availble to cook");
                            notificationProvider.publish(new KitchenOutOfFoodBuilder().build());
                            return Futures.immediateFailedCheckedFuture(
                                    new TransactionCommitFailedException("", cookNoMoreMealError()));
                        }

                        LOG.info("Setting Camera status to Preheating");
                        // We're not currently cooking food - we try to update the status to On
                        // to indicate we're going to cook food. This acts as a lock to prevent
                        // concurrent cooking.
                        tx.put(LogicalDatastoreType.OPERATIONAL, OVEN_IID,
                                buildOvenParams(OvenStatus.Preheating));
                        return tx.submit();
                    }
                    LOG.info("The oven is actually on use, cancel actual program before.");
                    // Return an error since we are already cooking food. This will get
                    // propagated to the commitFuture below which will interpret the null
                    // TransactionStatus in the RpcResult as an error condition.
                    return Futures.immediateFailedCheckedFuture(
                            new TransactionCommitFailedException("", cookOvenInUseError()));
                }

                private RpcError cookNoMoreMealError() {
                    return RpcResultBuilder.newError( ErrorType.APPLICATION, "resource-denied",
                            "No more food available to cook", "out-of-stock", null, null );
                }
            });
        Futures.addCallback(commitFuture, new FutureCallback<Void>() {
            @Override
            public void onFailure(Throwable t) {
                if (t instanceof OptimisticLockFailedException) {
                    // Another thread is likely trying to cook food simultaneously and updated the
                    // status before us. Try reading the status again - if another cookFood is
                    // now in progress, we should get OvenStatus.Waiting and fail.
                    if ((tries - 1) > 0) {
                        LOG.info("Got OptimisticLockFailedException - trying again");
                        checkStatusAndCookFood(input, futureResult, tries - 1);
                    } else {
                        futureResult.set(RpcResultBuilder.<Void> failed()
                                .withError(ErrorType.APPLICATION,
                                        t.getMessage()).build());
                    }
                } else {
                    LOG.info("Failed to commit Oven status", t);
                    // Probably already cooking.
                    futureResult.set(RpcResultBuilder.<Void> failed()
                            .withRpcErrors(((TransactionCommitFailedException) t)
                                    .getErrorList()).build());
                }
            }

            @Override
            public void onSuccess(Void result) {
                // OK to cook
                currentCookingMealTask.set(executor.submit(new CookMealTask(
                        input, futureResult)));

            }

        });
    }

    private RpcError cookOvenInUseError() {
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
            // cookMeal sleeps for n seconds for every 1 unit change of
            // timer level
            LOG.info("Inside CookMealTask's call() method");
            LOG.info("Temperature is:" + mealRequest.getTemperature());
            LOG.info("Timer is:" + mealRequest.getTimer());
            if(mealRequest.getProgram() != null)
                LOG.info("Program is:" +mealRequest.getProgram().getName());
            try {
                final int timer = OvenProvider.this.timer.get();

                //Get the thermostat constant if present
                int tF=1;
                if(OvenProvider.this.thermostat!=null)
                    tF = OvenProvider.this.thermostat.get();
                setOvenStatusUp(new Function<Boolean, Void>() {
                    @Override
                    public Void apply(Boolean result) {
                        currentCookingMealTask.set(null);
                        LOG.info("Oven Ready @setOvenStatusCooking.apply()");
                        futureResult.set(RpcResultBuilder.<Void> success().build());
                        return null;
                    }
                });
                Thread.sleep(Math.abs(tF*(timer)));
            } catch (final InterruptedException e) {
                LOG.info("Interrupted while cooking food.");
            }
            mealCooked.incrementAndGet();
            numberOfMealAvailable.getAndDecrement();
            // need to redo this even though it is already handled in checkStatusAndCookFood
            // before it gets here. Since if the first time getAndDecrement() on numberOfMeals
            // available gets called and is 0 then publish Notif
            if(numberOfMealAvailable.get()==0) {
                LOG.info("Oven out of food to cook meal!");
                notificationProvider.publish(new KitchenOutOfFoodBuilder().build());
            }

            // Set the Oven status back to waiting - this essentially unloads the oven.
            // We can't clear the current cookMealTask nor set the Future result until the
            // update has been committed so we pass a callback to be notified on completion.

            //To-Do:  Instead of Sleep here, add a callback to syncOvenParamWithDataStore
            //to return a boolean to let you know about the completion of cookFood,
            //if true then only reset the OvenStatus to waiting.
            Thread.sleep(10);
            setOvenStatusWaiting(new Function<Boolean, Void>() {
                @Override
                public Void apply(Boolean result) {
                    currentCookingMealTask.set(null);
                    LOG.info("Oven Ready @setOvenStatusWaiting.apply()");
                    futureResult.set(RpcResultBuilder.<Void> success().build());
                    return null;
                }
            });
            return null;
        }
    }

    private void setOvenStatusWaiting( final Function<Boolean, Void> resultCallback ) {
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

    private void setOvenStatusUp( final Function<Boolean,Void> resultCallback ) {

        final WriteTransaction tx = db.newWriteOnlyTransaction();
        tx.put( LogicalDatastoreType.OPERATIONAL, OVEN_IID, buildOvenParams( OvenStatus.Cooking ) );

        Futures.addCallback( tx.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess( final Void result ) {
                notifyCallback( true );
            }

            @Override
            public void onFailure( final Throwable t ) {
                // We shouldn't get an OptimisticLockFailedException (or any ex) as no
                // other component should be updating the operational state.
                LOG.error( "Failed to update oven status", t );

                notifyCallback( false );
            }

            void notifyCallback( final boolean result ) {
                if( resultCallback != null ) {
                    resultCallback.apply( result );
                }
            }
        } );
    }

    @Override
    public Future<RpcResult<Void>> listProgram() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Future<RpcResult<Void>> restockFood(RestockFoodInput input) {
        LOG.info("currentNumberOfMeal:" +numberOfMealAvailable.get()+ " | RestockFoodInput:" +input);
        final Long currentNumberOfMeal = numberOfMealAvailable.get();
        numberOfMealAvailable.set(currentNumberOfMeal+input.getAmountOfFoodToStock());
        if(input.getAmountOfFoodToStock()>0) {
            final KitchenRestocked kitchenRestockedNotif= new KitchenRestockedBuilder().setNumberOfMeal(input.getAmountOfFoodToStock()).build();
            notificationProvider.publish(kitchenRestockedNotif);
        }

        return Futures.immediateFuture( RpcResultBuilder.<Void> success().build() );
    }

}
