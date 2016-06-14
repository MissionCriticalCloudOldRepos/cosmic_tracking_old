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
package org.apache.cloudstack.affinity.dao;

import java.util.List;

import com.cloud.utils.db.GenericDao;

import org.apache.cloudstack.affinity.AffinityGroupVO;

public interface AffinityGroupDao extends GenericDao<AffinityGroupVO, Long> {
    List<AffinityGroupVO> listByAccountId(long accountId);

    boolean isNameInUse(Long accountId, Long domainId, String name);

    AffinityGroupVO findByAccountAndName(Long accountId, String name);

    List<AffinityGroupVO> findByAccountAndNames(Long accountId, String... names);

    int removeByAccountId(long accountId);

    AffinityGroupVO findDomainLevelGroupByName(Long domainId, String affinityGroupName);

    AffinityGroupVO findByAccountAndType(Long accountId, String string);

    AffinityGroupVO findDomainLevelGroupByType(Long domainId, String string);
}
