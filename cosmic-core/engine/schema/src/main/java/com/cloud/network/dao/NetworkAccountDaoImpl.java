// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.network.dao;

import com.cloud.utils.db.GenericDaoBase;
import com.cloud.utils.db.SearchBuilder;
import com.cloud.utils.db.SearchCriteria;
import com.cloud.utils.db.SearchCriteria.Op;

import org.springframework.stereotype.Component;

@Component
public class NetworkAccountDaoImpl extends GenericDaoBase<NetworkAccountVO, Long> implements NetworkAccountDao {
    final SearchBuilder<NetworkAccountVO> AllFieldsSearch;

    protected NetworkAccountDaoImpl() {
        super();

        AllFieldsSearch = createSearchBuilder();
        AllFieldsSearch.and("networkId", AllFieldsSearch.entity().getNetworkId(), Op.EQ);
        AllFieldsSearch.done();
    }

    @Override
    public NetworkAccountVO getAccountNetworkMapByNetworkId(long networkId) {
        SearchCriteria<NetworkAccountVO> sc = AllFieldsSearch.create();
        sc.setParameters("networkId", networkId);
        return findOneBy(sc);
    }
}
