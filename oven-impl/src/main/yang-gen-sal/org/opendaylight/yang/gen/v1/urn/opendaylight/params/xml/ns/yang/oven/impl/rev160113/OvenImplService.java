package org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.oven.impl.rev160113;
import org.opendaylight.yangtools.yang.binding.RpcService;
import org.opendaylight.yangtools.yang.common.RpcResult;
import java.util.concurrent.Future;


/**
 * Interface for implementing the following YANG RPCs defined in module &lt;b&gt;oven-impl&lt;/b&gt;
 * &lt;br&gt;(Source path: &lt;i&gt;META-INF/yang/oven-impl.yang&lt;/i&gt;):
 * &lt;pre&gt;
 * rpc clear-meal-cooked-rpc {
 *     "JMX call to clear the meal-cooked counter.";
 *     input {
 *         leaf context-instance {
 *             type instance-identifier;
 *         }
 *     }
 *     
 *     status CURRENT;
 * }
 * &lt;/pre&gt;
 *
 */
public interface OvenImplService
    extends
    RpcService
{




    /**
     * JMX call to clear the meal-cooked counter.
     *
     */
    Future<RpcResult<java.lang.Void>> clearMealCookedRpc(ClearMealCookedRpcInput input);

}

