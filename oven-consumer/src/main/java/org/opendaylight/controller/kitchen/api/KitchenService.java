package org.opendaylight.controller.kitchen.api;

import java.util.concurrent.Future;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.rev160113.FoodType;
import org.opendaylight.yangtools.yang.common.RpcResult;

public interface KitchenService {

    Future<RpcResult<Void>> makeMeal( AccompanimentType accompaniment, Class<? extends FoodType> foodType, int temperature, int timer );

}
