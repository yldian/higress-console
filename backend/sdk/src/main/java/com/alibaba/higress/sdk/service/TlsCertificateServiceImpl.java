/*
 * Copyright (c) 2022-2023 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.alibaba.higress.sdk.service;

import java.util.Collections;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;

import com.alibaba.higress.sdk.constant.KubernetesConstants;
import com.alibaba.higress.sdk.exception.BusinessException;
import com.alibaba.higress.sdk.exception.ResourceConflictException;
import com.alibaba.higress.sdk.http.HttpStatus;
import com.alibaba.higress.sdk.model.CommonPageQuery;
import com.alibaba.higress.sdk.model.PaginatedResult;
import com.alibaba.higress.sdk.model.TlsCertificate;
import com.alibaba.higress.sdk.service.kubernetes.KubernetesClientService;
import com.alibaba.higress.sdk.service.kubernetes.KubernetesModelConverter;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Secret;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class TlsCertificateServiceImpl implements TlsCertificateService {

    private final KubernetesClientService kubernetesClientService;
    private final KubernetesModelConverter kubernetesModelConverter;

    public TlsCertificateServiceImpl(KubernetesClientService kubernetesClientService,
        KubernetesModelConverter kubernetesModelConverter) {
        this.kubernetesClientService = kubernetesClientService;
        this.kubernetesModelConverter = kubernetesModelConverter;
    }

    @Override
    public PaginatedResult<TlsCertificate> list(CommonPageQuery query) {
        List<V1Secret> secrets;
        try {
            secrets = kubernetesClientService.listSecret(KubernetesConstants.SECRET_TYPE_TLS);
        } catch (ApiException e) {
            throw new BusinessException("Error occurs when listing Secret.", e);
        }
        if (CollectionUtils.isEmpty(secrets)) {
            return PaginatedResult.createFromFullList(Collections.emptyList(), query);
        }
        return PaginatedResult.createFromFullList(secrets, query, kubernetesModelConverter::secret2TlsCertificate);
    }

    @Override
    public TlsCertificate query(String name) {
        V1Secret secret;
        try {
            secret = kubernetesClientService.readSecret(name);
        } catch (ApiException e) {
            throw new BusinessException("Error occurs when reading the Secret with name: " + name, e);
        }
        if (secret == null) {
            return null;
        }
        if (!KubernetesConstants.SECRET_TYPE_TLS.equals(secret.getType())) {
            return null;
        }
        return kubernetesModelConverter.secret2TlsCertificate(secret);
    }

    @Override
    public TlsCertificate add(TlsCertificate certificate) {
        V1Secret secret = kubernetesModelConverter.tlsCertificate2Secret(certificate);
        V1Secret newSecret;
        try {
            newSecret = kubernetesClientService.createSecret(secret);
        } catch (ApiException e) {
            if (e.getCode() == HttpStatus.CONFLICT) {
                throw new ResourceConflictException();
            }
            throw new BusinessException("Error occurs when updating the secret generated by tls certificate with name: "
                + certificate.getName(), e);
        }
        return kubernetesModelConverter.secret2TlsCertificate(newSecret);
    }

    @Override
    public TlsCertificate update(TlsCertificate tlsCertificate) {
        V1Secret secret = kubernetesModelConverter.tlsCertificate2Secret(tlsCertificate);
        V1Secret newSecret;
        try {
            newSecret = kubernetesClientService.replaceSecret(secret);
        } catch (ApiException e) {
            if (e.getCode() == HttpStatus.CONFLICT) {
                throw new ResourceConflictException();
            }
            throw new BusinessException("Error occurs when updating the secret generated by tls certificate with name: "
                + tlsCertificate.getName(), e);
        }
        return kubernetesModelConverter.secret2TlsCertificate(newSecret);
    }

    @Override
    public void delete(String name) {
        try {
            kubernetesClientService.deleteSecret(name);
        } catch (ApiException e) {
            throw new BusinessException("Error occurs when deleting secret with name: " + name, e);
        }
    }
}
