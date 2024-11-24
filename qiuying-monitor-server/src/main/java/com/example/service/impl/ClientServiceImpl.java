package com.example.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.entity.dto.Client;
import com.example.entity.dto.ClientDetail;
import com.example.entity.vo.request.ClientDetailVO;
import com.example.entity.vo.request.RenameClientVO;
import com.example.entity.vo.request.RenameNodeVO;
import com.example.entity.vo.request.RuntimeDetailVO;
import com.example.entity.vo.response.ClientDetailsVO;
import com.example.entity.vo.response.ClientPreviewVO;
import com.example.mapper.ClientDetailMapper;
import com.example.mapper.ClientMapper;
import com.example.service.ClientService;
import com.example.utils.InfluxDbUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ClientServiceImpl extends ServiceImpl<ClientMapper, Client> implements ClientService {

    private String registerToken = this.generateNewToken();

    private final Map<Integer, Client> clientIdCache = new ConcurrentHashMap<>();
    private final Map<String, Client> tokenTokenCache = new ConcurrentHashMap<>();

    @Resource
    ClientDetailMapper detailMapper;

    @Resource
    InfluxDbUtils influx;

    @PostConstruct
    public void initClientCache() {
        this.list().forEach(this::addClientCache);
    }

    @Override
    public String registerToken() {
        return registerToken;
    }

    @Override
    public Client findClientById(int id) {
        return clientIdCache.get(id);
    }

    @Override
    public Client findClientByToken(String token) {
        return tokenTokenCache.get(token);
    }

    @Override
    public boolean verifyAndRegister(String token) {
        if (this.registerToken.equals(token)) {
            int id = this.randomClientId();
            Client client = new Client(id, "喵喵喵", token, "cn", "中南海节点", new Date());
            if (this.save(client)) {
                registerToken = this.generateNewToken();
                this.addClientCache(client);
                return true;
            }
        }
        return false;
    }

    @Override
    public void updateClientDetail(ClientDetailVO vo, Client client) {
        ClientDetail detail = new ClientDetail();
        BeanUtils.copyProperties(vo, detail);
        detail.setId(client.getId());
        if (Objects.nonNull(detailMapper.selectById(client.getId()))) {
            detailMapper.updateById(detail);
        } else {
            detailMapper.insert(detail);
        }
    }

    private Map<Integer, RuntimeDetailVO> currentRuntime = new ConcurrentHashMap<>();

    @Override
    public void updateRuntimeDetail(RuntimeDetailVO vo, Client client) {
        currentRuntime.put(client.getId(), vo);
        influx.writeRuntimeData(client.getId(), vo);
    }

    @Override
    public List<ClientPreviewVO> listClients() {

        return clientIdCache.values().stream().map(client -> {
            ClientPreviewVO vo = client.asViewObject(ClientPreviewVO.class);
            BeanUtils.copyProperties(detailMapper.selectById(vo.getId()), vo);
            RuntimeDetailVO runtime = currentRuntime.get(client.getId());
            if (this.isOnline(runtime)) {
                BeanUtils.copyProperties(runtime, vo);
                vo.setOnline(true);
            }
            return vo;
        }).toList();
    }

    @Override
    public void renameClient(RenameClientVO vo) {
        this.update(Wrappers.<Client>update().eq("id", vo.getId()).set("name", vo.getName()));
        this.initClientCache();
    }

    @Override
    public void renameNode(RenameNodeVO vo) {
        this.update(Wrappers.<Client>update().eq("id", vo.getId())
                .set("node", vo.getNode()).set("location", vo.getLocation()));
        this.initClientCache();
    }

    @Override
    public ClientDetailsVO clientDetails(int clientId) {
        ClientDetailsVO vo = this.clientIdCache.get(clientId).asViewObject(ClientDetailsVO.class);
        BeanUtils.copyProperties(detailMapper.selectById(clientId), vo);
        vo.setOnline(this.isOnline(currentRuntime.get(clientId)));
        return vo;
    }

    private boolean isOnline(RuntimeDetailVO runtime) {
        return runtime != null && System.currentTimeMillis() - runtime.getTimestamp() < 60 * 1000;

    }

    private void addClientCache(Client client) {
        clientIdCache.put(client.getId(), client);
        tokenTokenCache.put(client.getToken(), client);
    }

    private int randomClientId() {
        return new Random().nextInt(90000000) + 10000000; // 生成一个10位数的随机数
    }

    private String generateNewToken() {
        String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(24);
        for (int i = 0; i < 24; i++)
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        System.out.println(sb);
        return sb.toString();
    }
}
