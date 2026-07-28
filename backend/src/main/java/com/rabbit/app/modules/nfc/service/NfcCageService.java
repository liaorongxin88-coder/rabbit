package com.rabbit.app.modules.nfc.service;

import com.rabbit.app.common.BizException;
import com.rabbit.app.modules.cage.entity.Cage;
import com.rabbit.app.modules.cage.mapper.CageMapper;
import com.rabbit.app.modules.dedup.service.RequestDedupService;
import com.rabbit.app.modules.house.service.HouseService;
import com.rabbit.app.modules.nfc.dto.BindNfcCageRequest;
import com.rabbit.app.modules.nfc.dto.NfcCageBindingView;
import com.rabbit.app.modules.nfc.dto.NfcCageQueueItem;
import com.rabbit.app.modules.nfc.dto.NfcCageQueueRow;
import com.rabbit.app.modules.nfc.dto.ResolveNfcCageRequest;
import com.rabbit.app.modules.nfc.entity.CageNfcTag;
import com.rabbit.app.modules.nfc.entity.NfcTag;
import com.rabbit.app.modules.nfc.mapper.CageNfcTagMapper;
import com.rabbit.app.modules.nfc.mapper.NfcTagMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NfcCageService {
    public static final int ERROR_TAG_UNREGISTERED = 4603;
    public static final int ERROR_UID_MISMATCH = 4604;
    public static final int ERROR_CAGE_DISABLED = 4605;
    public static final int ERROR_BINDING_CONFLICT = 4606;
    private static final String TARGET_CAGE = "CAGE";
    private static final String BIND_API = "nfc:cage:bind";

    private final HouseService houseService;
    private final CageMapper cageMapper;
    private final NfcTagMapper nfcTagMapper;
    private final CageNfcTagMapper cageNfcTagMapper;
    private final NfcCagePayloadCodec payloadCodec;
    private final RequestDedupService requestDedupService;

    public NfcCageService(
            HouseService houseService,
            CageMapper cageMapper,
            NfcTagMapper nfcTagMapper,
            CageNfcTagMapper cageNfcTagMapper,
            NfcCagePayloadCodec payloadCodec,
            RequestDedupService requestDedupService
    ) {
        this.houseService = houseService;
        this.cageMapper = cageMapper;
        this.nfcTagMapper = nfcTagMapper;
        this.cageNfcTagMapper = cageNfcTagMapper;
        this.payloadCodec = payloadCodec;
        this.requestDedupService = requestDedupService;
    }

    public List<NfcCageQueueItem> listWriteQueue(Long userId, Long houseId) {
        houseService.assertHousePermission(userId, houseId, "control");
        List<NfcCageQueueItem> result = new ArrayList<NfcCageQueueItem>();
        for (NfcCageQueueRow row : nfcTagMapper.selectCageQueue(houseId)) {
            NfcCageQueueItem item = new NfcCageQueueItem();
            item.setCageId(row.getCageId());
            item.setCageNumber(row.getCageNumber());
            item.setPayload(payloadCodec.create(houseId, row.getCageId()));
            String genericUid = normalizeUid(row.getGenericTagUid());
            String cageUid = normalizeUid(row.getCageTagUid());
            if (genericUid.isEmpty() && cageUid.isEmpty()) {
                item.setBindingStatus("UNBOUND");
                item.setTagUid(null);
            } else if (!genericUid.isEmpty() && genericUid.equals(cageUid)) {
                item.setBindingStatus("BOUND");
                item.setTagUid(genericUid);
            } else {
                item.setBindingStatus("CONFLICT");
                item.setTagUid(genericUid.isEmpty() ? cageUid : genericUid);
            }
            result.add(item);
        }
        return result;
    }

    @Transactional
    public NfcCageBindingView bind(Long userId, Long houseId, BindNfcCageRequest request) {
        RequestDedupService.BeginResult beginResult =
                requestDedupService.begin(houseId, userId, BIND_API, request.getRequestId());
        if (beginResult == RequestDedupService.BeginResult.DONE) {
            houseService.assertHousePermission(userId, houseId, "control");
            NfcCagePayloadCodec.ParsedPayload parsed = payloadCodec.verify(request.getPayload());
            Cage existingCage = cageMapper.selectById(houseId, request.getCageId());
            String uid = normalizeUid(request.getTagUid());
            NfcTag existingGeneric = nfcTagMapper.selectByHouseAndUid(houseId, uid);
            CageNfcTag existingCageTag = cageNfcTagMapper.selectByHouseAndUid(houseId, uid);
            if (parsed.houseId() != houseId.longValue()
                    || parsed.cageId() != request.getCageId().longValue()
                    || existingCage == null
                    || existingGeneric == null
                    || existingCageTag == null
                    || !matches(existingGeneric, houseId, request.getCageId(), uid)
                    || !matches(existingCageTag, houseId, request.getCageId(), uid)) {
                throw new BizException(ERROR_BINDING_CONFLICT, "requestId已用于其他NFC绑定");
            }
            return toView(houseId, existingCage, uid, "BOUND");
        }
        try {
            houseService.assertHousePermission(userId, houseId, "control");
            NfcCagePayloadCodec.ParsedPayload parsed = payloadCodec.verify(request.getPayload());
            if (parsed.houseId() != houseId.longValue() || parsed.cageId() != request.getCageId().longValue()) {
                throw new BizException(NfcCagePayloadCodec.ERROR_INVALID_SIGNATURE, "NFC标签目标与当前笼位不一致");
            }

            Cage cage = cageMapper.selectById(houseId, request.getCageId());
            if (cage == null || Boolean.FALSE.equals(cage.getIsEnabled())) {
                throw new BizException(ERROR_CAGE_DISABLED, "笼位不存在或已停用");
            }
            String uid = normalizeUid(request.getTagUid());
            if (uid.isEmpty()) {
                throw new BizException(ERROR_TAG_UNREGISTERED, "无法读取标签UID");
            }

            NfcTag genericByUid = nfcTagMapper.selectByHouseAndUid(houseId, uid);
            NfcTag genericByTarget = nfcTagMapper.selectByHouseAndTarget(houseId, TARGET_CAGE, request.getCageId());
            CageNfcTag cageByUid = cageNfcTagMapper.selectByHouseAndUid(houseId, uid);
            CageNfcTag cageByCage = cageNfcTagMapper.selectByHouseAndCage(houseId, request.getCageId());
            boolean conflict = conflicts(genericByUid, genericByTarget, cageByUid, cageByCage, houseId, request.getCageId(), uid);
            if (conflict && !Boolean.TRUE.equals(request.getReplaceExisting())) {
                throw new BizException(ERROR_BINDING_CONFLICT, "标签或笼位已绑定，请确认后重新绑定");
            }
            if (conflict) {
                removeConflictingBindings(houseId, request.getCageId(), uid);
            }

            NfcTag genericTag = new NfcTag();
            genericTag.setHouseId(houseId);
            genericTag.setTagUid(uid);
            genericTag.setTargetType(TARGET_CAGE);
            genericTag.setTargetId(request.getCageId());
            genericTag.setRequestId(request.getRequestId());
            genericTag.setRemark("签名NDEF笼位标签");
            genericTag.setCreateBy(String.valueOf(userId));
            genericTag.setUpdateBy(String.valueOf(userId));
            nfcTagMapper.upsert(genericTag);

            CageNfcTag cageTag = new CageNfcTag();
            cageTag.setHouseId(houseId);
            cageTag.setCageId(request.getCageId());
            cageTag.setTagUid(uid);
            cageTag.setRequestId(request.getRequestId());
            cageTag.setRemark("签名NDEF笼位标签");
            cageTag.setCreateBy(String.valueOf(userId));
            cageTag.setUpdateBy(String.valueOf(userId));
            cageNfcTagMapper.upsert(cageTag);

            requestDedupService.markDone(houseId, userId, BIND_API, request.getRequestId());
            return toView(houseId, cage, uid, "BOUND");
        } catch (RuntimeException e) {
            requestDedupService.markFailed(houseId, userId, BIND_API, request.getRequestId(), e.getMessage());
            throw e;
        }
    }

    public NfcCageBindingView resolve(Long userId, Long houseId, ResolveNfcCageRequest request) {
        NfcCagePayloadCodec.ParsedPayload parsed = payloadCodec.verify(request.getPayload());
        if (parsed.houseId() != houseId.longValue()) {
            throw new BizException(NfcCagePayloadCodec.ERROR_INVALID_SIGNATURE, "NFC标签不属于当前兔舍");
        }
        houseService.assertHousePermission(userId, houseId, "view");
        Cage cage = cageMapper.selectById(houseId, parsed.cageId());
        if (cage == null || Boolean.FALSE.equals(cage.getIsEnabled())) {
            throw new BizException(ERROR_CAGE_DISABLED, "笼位不存在或已停用");
        }
        String uid = normalizeUid(request.getTagUid());
        NfcTag generic = uid.isEmpty() ? null : nfcTagMapper.selectByHouseAndUid(houseId, uid);
        CageNfcTag cageTag = uid.isEmpty() ? null : cageNfcTagMapper.selectByHouseAndUid(houseId, uid);
        if (generic == null || cageTag == null) {
            throw new BizException(ERROR_TAG_UNREGISTERED, "标签尚未完成绑定");
        }
        if (!TARGET_CAGE.equals(generic.getTargetType())
                || generic.getTargetId() == null
                || generic.getTargetId().longValue() != parsed.cageId()
                || cageTag.getCageId() == null
                || cageTag.getCageId().longValue() != parsed.cageId()) {
            throw new BizException(ERROR_UID_MISMATCH, "标签UID与笼位绑定不一致");
        }
        return toView(houseId, cage, uid, "BOUND");
    }

    private boolean conflicts(
            NfcTag genericByUid,
            NfcTag genericByTarget,
            CageNfcTag cageByUid,
            CageNfcTag cageByCage,
            Long houseId,
            Long cageId,
            String uid
    ) {
        return !matches(genericByUid, houseId, cageId, uid)
                || !matches(genericByTarget, houseId, cageId, uid)
                || !matches(cageByUid, houseId, cageId, uid)
                || !matches(cageByCage, houseId, cageId, uid);
    }

    private boolean matches(NfcTag tag, Long houseId, Long cageId, String uid) {
        if (tag == null) {
            return true;
        }
        return houseId.equals(tag.getHouseId())
                && TARGET_CAGE.equals(tag.getTargetType())
                && cageId.equals(tag.getTargetId())
                && uid.equals(normalizeUid(tag.getTagUid()));
    }

    private boolean matches(CageNfcTag tag, Long houseId, Long cageId, String uid) {
        if (tag == null) {
            return true;
        }
        return houseId.equals(tag.getHouseId())
                && cageId.equals(tag.getCageId())
                && uid.equals(normalizeUid(tag.getTagUid()));
    }

    private void removeConflictingBindings(Long houseId, Long cageId, String uid) {
        nfcTagMapper.deleteByHouseAndUid(houseId, uid);
        nfcTagMapper.deleteByHouseAndTarget(houseId, TARGET_CAGE, cageId);
        cageNfcTagMapper.deleteByHouseAndUid(houseId, uid);
        cageNfcTagMapper.deleteByHouseAndCage(houseId, cageId);
    }

    private NfcCageBindingView toView(Long houseId, Cage cage, String uid, String status) {
        NfcCageBindingView view = new NfcCageBindingView();
        view.setHouseId(houseId);
        view.setCageId(cage == null ? null : cage.getId());
        view.setCageNumber(cage == null ? null : cage.getCageNumber());
        view.setTagUid(uid);
        view.setBindingStatus(status);
        return view;
    }

    private String normalizeUid(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
