/*
 *    Copyright 2017 Benjamin K (darkevilmac)
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.elytradev.teckle.common.worldnetwork.common.node;

import com.elytradev.teckle.api.IWorldNetwork;
import com.elytradev.teckle.common.worldnetwork.common.WorldNetworkTraveller;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

/**
 * Created by darkevilmac on 3/26/2017.
 */
public abstract class WorldNetworkEndpoint extends WorldNetworkNode {

    public WorldNetworkEndpoint(IWorldNetwork network, BlockPos position, EnumFacing capabilityFace) {
        super(network, position, capabilityFace);
    }

    /**
     * Inject the traveller into the endpoint.
     *
     * @param traveller
     * @param from
     * @return if injection was successful, and no product is remaining.
     */
    public abstract boolean inject(WorldNetworkTraveller traveller, EnumFacing from);

}