package com.example.reviewsystem.service;

import com.example.reviewsystem.dto.response.ExhibitionBoardItem;
import com.example.reviewsystem.entity.Handover;
import com.example.reviewsystem.entity.Work;
import com.example.reviewsystem.exception.BusinessConflictException;
import com.example.reviewsystem.exception.BusinessValidationException;
import com.example.reviewsystem.repository.HandoverRepository;
import com.example.reviewsystem.repository.WorkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实数据库（H2/MySQL 兼容模式）端到端：现场点交状态机、三要素校验、
 * 失败整单作废、展陈看板只列已点交、参展凭证门槛、件数变更撤墙撤证、重新点交。
 */
@SpringBootTest(classes = PublicationWorkflowIntegrationTest.TestApp.class)
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
@Transactional
class HandoverWorkflowIntegrationTest {

    @Autowired HandoverService handoverService;
    @Autowired ExhibitionService exhibitionService;
    @Autowired WorkService workService;
    @Autowired WorkRepository workRepository;
    @Autowired HandoverRepository handoverRepository;

    private Work createWork(String name, Integer pieceCount) {
        return workRepository.save(Work.builder()
                .workName(name).category("手作").creatorName(name + "作者")
                .status("APPROVED").published(false).isQualified(false)
                .pieceCount(pieceCount).build());
    }

    private Work reload(Long id) {
        return workRepository.findById(id).orElseThrow();
    }

    @Test
    void fullHandoverWorkflow_wallAndCertificateFollowHandover() {
        Work work = createWork("青瓷茶具", 2);
        Long id = work.getId();
        assertEquals(HandoverService.STATUS_NOT_STARTED, reload(id).getHandoverStatus());

        // 未点交：看板没有它，凭证领不出
        assertTrue(exhibitionService.getBoard().isEmpty());
        assertThrows(BusinessConflictException.class, () -> exhibitionService.issueCertificate(id));

        // 未点交不能直接完成点交（必须逐步推进）
        assertThrows(BusinessConflictException.class,
                () -> handoverService.completeHandover(id, "完好", "张三"));

        // 开始点交 → 点交中，仍不能上墙、不能领证
        handoverService.startHandover(id);
        assertEquals(HandoverService.STATUS_IN_PROGRESS, reload(id).getHandoverStatus());
        assertTrue(exhibitionService.getBoard().isEmpty());
        assertThrows(BusinessConflictException.class, () -> exhibitionService.issueCertificate(id));

        // 只登记接收人就想完成 → 缺完好情况，点交单不成立
        handoverService.updateDraft(id, null, "张三");
        assertThrows(BusinessValidationException.class,
                () -> handoverService.completeHandover(id, null, null));
        assertEquals(HandoverService.STATUS_IN_PROGRESS, reload(id).getHandoverStatus());

        // 补齐完好情况 → 已点交
        handoverService.completeHandover(id, "完好", null);
        Work completed = reload(id);
        assertEquals(HandoverService.STATUS_COMPLETED, completed.getHandoverStatus());
        Handover handover = handoverRepository.findByWorkId(id).orElseThrow();
        assertEquals(2, handover.getPieceCountSnapshot());
        assertEquals("完好", handover.getConditionStatus());
        assertEquals("张三", handover.getReceiver());
        assertNotNull(handover.getCompletedAt());

        // 已点交：上墙，且能领参展凭证
        List<ExhibitionBoardItem> board = exhibitionService.getBoard();
        assertEquals(1, board.size());
        assertEquals("青瓷茶具", board.get(0).getWorkName());
        assertEquals(2, board.get(0).getPieceCount());
        Work issued = exhibitionService.issueCertificate(id);
        assertEquals(ExhibitionService.CERT_ISSUED, issued.getCertificateStatus());
        assertNotNull(issued.getCertificateNo());
    }

    @Test
    void failHandover_leavesNoPartialDraft() {
        Work work = createWork("半途而废", 1);
        Long id = work.getId();

        handoverService.startHandover(id);
        // 登记了一半：完好情况与接收人都已填
        handoverService.updateDraft(id, "完好", "李四");
        assertTrue(handoverRepository.findByWorkId(id).isPresent());

        // 点交失败 → 整单作废，作品退回未点交
        handoverService.failHandover(id);
        assertEquals(HandoverService.STATUS_NOT_STARTED, reload(id).getHandoverStatus());
        assertTrue(handoverRepository.findByWorkId(id).isEmpty(),
                "失败的点交不能留下一半件数一半空着的点交单");
        assertTrue(exhibitionService.getBoard().isEmpty());
        assertThrows(BusinessConflictException.class, () -> exhibitionService.issueCertificate(id));

        // 可以重新开箱点交
        handoverService.startHandover(id);
        Handover fresh = handoverRepository.findByWorkId(id).orElseThrow();
        assertNull(fresh.getConditionStatus(), "重开的点交单不得残留上次登记的内容");
        assertNull(fresh.getReceiver());
    }

    @Test
    void pieceCountChangeAfterCompletion_pullsWallAndCertificate_untilRehandover() {
        Work work = createWork("改件数的作品", 3);
        Long id = work.getId();

        // 走完点交并领证
        handoverService.startHandover(id);
        handoverService.completeHandover(id, "完好", "王五");
        exhibitionService.issueCertificate(id);
        assertEquals(1, exhibitionService.getBoard().size());
        String oldCertNo = reload(id).getCertificateNo();
        assertNotNull(oldCertNo);

        // 作者把件数从 3 改成 5 → 点交单作废、展墙撤下、凭证撤销
        workService.updateWork(id, Work.builder().pieceCount(5).status(null).build());
        Work changed = reload(id);
        assertEquals(5, changed.getPieceCount());
        assertEquals(HandoverService.STATUS_NOT_STARTED, changed.getHandoverStatus());
        assertTrue(handoverRepository.findByWorkId(id).isEmpty(), "旧点交单必须作废删除");
        assertEquals(ExhibitionService.CERT_REVOKED, changed.getCertificateStatus());
        assertNotNull(changed.getCertificateRevokedAt());
        assertTrue(exhibitionService.getBoard().isEmpty(), "撤下后看板不得再列出该作品");
        assertThrows(BusinessConflictException.class, () -> exhibitionService.issueCertificate(id));

        // 重新点交完成前不能上墙；走完后恢复上墙、可重新领证
        handoverService.startHandover(id);
        handoverService.completeHandover(id, "完好", "王五");
        assertEquals(HandoverService.STATUS_COMPLETED, reload(id).getHandoverStatus());
        List<ExhibitionBoardItem> board = exhibitionService.getBoard();
        assertEquals(1, board.size());
        assertEquals(5, board.get(0).getPieceCount(), "看板件数必须是新点交钉下的 5 件");
        Work reissued = exhibitionService.issueCertificate(id);
        assertEquals(ExhibitionService.CERT_ISSUED, reissued.getCertificateStatus());
        assertFalse(oldCertNo.equals(reissued.getCertificateNo()), "重新发证必须是新凭证号");
    }

    @Test
    void pieceCountChangeOnNotStartedWork_doesNotTouchAnything() {
        Work work = createWork("未点交改件数", 2);
        Long id = work.getId();

        workService.updateWork(id, Work.builder().pieceCount(7).status(null).build());
        Work changed = reload(id);
        assertEquals(7, changed.getPieceCount());
        assertEquals(HandoverService.STATUS_NOT_STARTED, changed.getHandoverStatus());
        assertEquals(ExhibitionService.CERT_NOT_ISSUED, changed.getCertificateStatus());
    }

    @Test
    void completeHandover_requiresPieceCountOnWork() {
        Work work = createWork("没填件数", null);
        Long id = work.getId();

        handoverService.startHandover(id);
        // 完好情况、接收人都齐了，但作品档案没件数 → 点交单仍不成立
        BusinessValidationException ex = assertThrows(BusinessValidationException.class,
                () -> handoverService.completeHandover(id, "完好", "张三"));
        assertTrue(ex.getMessage().contains("件数"));
        assertEquals(HandoverService.STATUS_IN_PROGRESS, reload(id).getHandoverStatus());

        // 补登件数后即可完成
        workService.updateWork(id, Work.builder().pieceCount(2).status(null).build());
        handoverService.completeHandover(id, null, null);
        assertEquals(HandoverService.STATUS_COMPLETED, reload(id).getHandoverStatus());
    }

}
