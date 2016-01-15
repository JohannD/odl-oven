package org.opendaylight.controller.kitchen.impl;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.opendaylight.controller.kitchen.api.KitchenService;
import org.opendaylight.controller.kitchen.api.AccompanimentType;
import org.opendaylight.controller.config.yang.config.kitchen_service.impl.KitchenServiceRuntimeMXBean;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ConsumerContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareConsumer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.OvenListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.KitchenOutOfFood;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.KitchenRestocked;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.OvenService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.CookFoodInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.CookFoodInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.Chicken;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.FoodType;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

public class KitchenServiceImpl implements KitchenService, AutoCloseable, BindingAwareConsumer, KitchenServiceRuntimeMXBean, OvenListener {

    private static final Logger log = LoggerFactory.getLogger( KitchenServiceImpl.class );
    private final OvenService oven;
    private final ListeningExecutorService executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
    private DataBroker dataBroker;
    private volatile boolean ovenOutOfFood;

    public KitchenServiceImpl(OvenService oven) {
        this.oven = oven;
    }
    @Override
    public void onKitchenOutOfFood(KitchenOutOfFood notification) {
        log.info("OvenOutOfFood notification");
        ovenOutOfFood=true;
    }

    @Override
    public void onKitchenRestocked(KitchenRestocked notification) {
        log.info("OvenRestocked notification-numberOfMeal" +notification.getNumberOfMeal());
        ovenOutOfFood=false;
    }

    @Override
    public Boolean makeChipsWithChickenRpc() {
        try {
            //This call has to block since we must return a result to the JMX client.
            final RpcResult<Void> result = makeMeal(AccompanimentType.CHIPS, Chicken.class, 180, 45).get();
            if(result.isSuccessful()) {
                log.info( "makeChipsWithChickenRpc succeeded" );
            } else {
                log.info( "makeChipsWithChickenRpc failed: " + result.getErrors() );
            }
            return result.isSuccessful();
        } catch(InterruptedException | ExecutionException e ) {
            log.info( "An error occurred while making breakfast: " + e );
        }
        return Boolean.FALSE;
    }

    @Override
    public void onSessionInitialized(ConsumerContext session) {
        this.dataBroker =  session.getSALService(DataBroker.class);

    }

    @Override
    public void close() throws Exception {
        executor.shutdown();
    }

    @Override
    public Future<RpcResult<Void>> makeMeal(AccompanimentType accompaniment, Class<? extends FoodType> foodType,
            int temperature, int timer) {
     // Call cookFood and use JdkFutureAdapters to convert the Future to a ListenableFuture,
        // The OpendaylightToaster impl already returns a ListenableFuture so the conversion is
        // actually a no-op.

        log.info("In makeMeal()");
        final ListenableFuture<RpcResult<Void>> cookFoodFuture = JdkFutureAdapters.listenInPoolThread(
                cookFood( foodType, temperature, timer ), executor );
        final ListenableFuture<RpcResult<Void>> makeMealFuture = cookAccompaniment(accompaniment);

        // Combine the 2 ListenableFutures into 1 containing a list of RpcResults and transform RpcResults into 1
        final ListenableFuture<List<RpcResult<Void>>> combinedFutures =
                Futures.allAsList( ImmutableList.of( cookFoodFuture, makeMealFuture ) );

        return Futures.transform(combinedFutures,
                new AsyncFunction<List<RpcResult<Void>>,RpcResult<Void>>() {
            @Override
            public ListenableFuture<RpcResult<Void>> apply( List<RpcResult<Void>> results ) throws Exception {
                boolean atLeastOneSucceeded = false;
                final Builder<RpcError> errorList = ImmutableList.builder();
                for( final RpcResult<Void> result: results ) {
                    if( result.isSuccessful() ) {
                        atLeastOneSucceeded = true;
                    }
                    if( result.getErrors() != null ) {
                        errorList.addAll( result.getErrors() );
                    }
                }
                return Futures.immediateFuture(
                        RpcResultBuilder.<Void> status(atLeastOneSucceeded).withRpcErrors(errorList.build()).build());
            }
        } );
    }

    private ListenableFuture<RpcResult<Void>> cookAccompaniment(AccompanimentType accompaniment) {
        log.info("In cookAccompaniment()");
        return executor.submit(new Callable<RpcResult<Void>>(){
            @Override
            public RpcResult<Void> call() throws Exception {
                // we don't actually do anything here, just return a successful result.
                return RpcResultBuilder.<Void> success().build();
            }
        });
    }

    private Future<RpcResult<Void>> cookFood(
            Class<? extends FoodType> foodType, int temperature, int timer) {
        if(ovenOutOfFood)
        {
            log.info("Out of food but we can reheat leftovers.");
            return Futures.immediateFuture(RpcResultBuilder.<Void> success().withWarning(ErrorType.APPLICATION, "partial operation", "Out of food but we can reheat leftovers.").build());
        }
        final CookFoodInput cookFoodInput = new CookFoodInputBuilder().setTimer(timer).setTemperature(temperature).setProgram(foodType).build();
        return oven.cookFood(cookFoodInput);
    }



}
