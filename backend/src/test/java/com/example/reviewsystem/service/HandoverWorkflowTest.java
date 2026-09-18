package com.example.reviewsystem.service;

import com.example.reviewsystem.dto.response.ExhibitionBoardItem;
import com.example.reviewsystem.entity.Handover;
import com.example.reviewsystem.entity.Work;
import com.example.reviewsystem.exception.BusinessConflictException;
import com.example.reviewsystem.exception.BusinessValidationException;
import com.example.reviewsystem.repository.HandoverRepository;
import com.example.reviewsystem.repository.WorkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 不启动 Spring 容器，用 Mockito 驱动真实的 HandoverService / ExhibitionService / WorkService，
 * 验证展厅主任口径：
 * 1) 点交单三要素（件数、完好情况、接收人）缺一样即不成立，作品停在点交中；
 * 2) 状态机只能 未点交 → 点交中 → 已点交，越级操作一律 409；
 * 3) 点交失败整单作废退回未点交，不留一半件数一半空着的半成品；
 * 4) 展陈看板只列已点交作品，参展凭证只对已点交作品发放；
 * 5) 已点交后作者改掉件数：点交单作废、展墙撤下、凭证撤销，重新点交前不得上墙。
 */
@ExtendWith(MockitoExtension.class)
class HandoverWorkflowTest {

    @Mock WorkRepository workRepository;
    @Mock HandoverRepository handoverRepository;

    HandoverService handoverService;
    ExhibitionService exhibitionService;
    WorkService workService;

    @BeforeEach
    void setUp() {
        handoverService = new HandoverService(handoverRepository, workRepository);
        exhibitionService = new ExhibitionService(workRepository, handoverRepository);
        workService = new WorkService(workRepository, handoverRepository, handoverService);
        lenient().when(workRepository.save(any(Work.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(handoverRepository.save(any(Handover.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Work work(Long id, String name, Integer pieceCount) {
        return Work.builder()
                .id(id).workName(name).category("绘画").creatorName("作者" + id)
                .status("APPROVED").published(false).isQualified(false)
                .pieceCount(pieceCount)
                .handoverStatus(HandoverService.STATUS_NOT_STARTED)
                .certificateStatus(ExhibitionService.CERT_NOT_ISSUED)
                .build();
    }

    private Handover draft(Long workId) {
        return Handover.builder()
                .id(workId * 10).workId(workId)
                .status(HandoverService.STATUS_IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .build();
    }

    // ---------- 三要素缺一不可 ----------

    @Test
    void completeHandover_missingPieceCount_isRejected() {
        Work work = work(10L, "缺件数", null);
        work.setHandoverStatus(HandoverService.STATUS_IN_PROGRESS);
        Handover handover = draft(10L);
        handover.setConditionStatus("完好");
        handover.setReceiver("张三");
        when(workRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(work));
        when(handoverRepository.findByWorkId(10L)).thenReturn(Optional.of(handover));

        BusinessValidationException ex = assertThrows(BusinessValidationException.class,
                () -> handoverService.completeHandover(10L, null, null));
        assertTrue(ex.getMessage().contains("件数"), "要点名缺的是件数: " + ex.getMessage());
        assertEquals(HandoverService.STATUS_IN_PROGRESS, work.getHandoverStatus(), "缺要素必须停在点交中");
        assertEquals(HandoverService.STATUS_IN_PROGRESS, handover.getStatus());
        verify(handoverRepository, never()).save(any());
    }

    @Test
    void completeHandover_missingCondition_isRejected() {
        Work work = work(11L, "缺完好情况", 3);
        work.setHandoverStatus(HandoverService.STATUS_IN_PROGRESS);
        Handover handover = draft(11L);
        handover.setReceiver("张三");
        when(workRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(work));
        when(handoverRepository.findByWorkId(11L)).thenReturn(Optional.of(handover));

        BusinessValidationException ex = assertThrows(BusinessValidationException.class,
                () -> handoverService.completeHandover(11L, null, null));
        assertTrue(ex.getMessage().contains("完好情况"));
        assertEquals(HandoverService.STATUS_IN_PROGRESS, work.getHandoverStatus());
        verify(handoverRepository, never()).save(any());
    }

    @Test
    void completeHandover_missingReceiver_isRejected() {
        Work work = work(12L, "缺接收人", 3);
        work.setHandoverStatus(HandoverService.STATUS_IN_PROGRESS);
        Handover handover = draft(12L);
        handover.setConditionStatus("完好");
        when(workRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(work));
        when(handoverRepository.findByWorkId(12L)).thenReturn(Optional.of(handover));

        BusinessValidationException ex = assertThrows(BusinessValidationException.class,
                () -> handoverService.completeHandover(12L, null, "  "));
        assertTrue(ex.getMessage().contains("接收人"), "空白接收人等同未填: " + ex.getMessage());
        assertEquals(HandoverService.STATUS_IN_PROGRESS, work.getHandoverStatus());
        verify(handoverRepository, never()).save(any());
    }

    // ---------- 状态机 ----------

    @Test
    void fullLifecycle_notStartedToCompleted() {
        Work work = work(13L, "全流程", 2);
        when(workRepository.findByIdForUpdate(13L)).thenReturn(Optional.of(work));
        when(handoverRepository.findByWorkId(13L)).thenReturn(Optional.empty());

        // 未点交 → 点交中
        Handover started = handoverService.startHandover(13L);
        assertEquals(HandoverService.STATUS_IN_PROGRESS, started.getStatus());
        assertEquals(HandoverService.STATUS_IN_PROGRESS, work.getHandoverStatus());
        assertNotNull(started.getStartedAt());

        // 点交中先记一半：只登记接收人
        when(handoverRepository.findByWorkId(13L)).thenReturn(Optional.of(started));
        handoverService.updateDraft(13L, null, "王五");
        assertEquals("王五", started.getReceiver());
        assertNull(started.getConditionStatus());

        // 完成点交时补上完好情况 → 已点交，件数快照钉在点交单上
        Handover completed = handoverService.completeHandover(13L, "完好", null);
        assertEquals(HandoverService.STATUS_COMPLETED, completed.getStatus());
        assertEquals(HandoverService.STATUS_COMPLETED, work.getHandoverStatus());
        assertEquals(2, completed.getPieceCountSnapshot());
        assertEquals("完好", completed.getConditionStatus());
        assertEquals("王五", completed.getReceiver());
        assertNotNull(completed.getCompletedAt());
    }

    @Test
    void completeFromNotStarted_isRejected() {
        Work work = work(14L, "越级完成", 2);
        when(workRepository.findByIdForUpdate(14L)).thenReturn(Optional.of(work));

        BusinessConflictException ex = assertThrows(BusinessConflictException.class,
                () -> handoverService.completeHandover(14L, "完好", "张三"));
        assertTrue(ex.getMessage().contains("未点交"));
        verify(handoverRepository, never()).save(any());
    }

    @Test
    void startWhenAlreadyInProgress_isRejected() {
        Work work = work(15L, "重复开始", 2);
        work.setHandoverStatus(HandoverService.STATUS_IN_PROGRESS);
        when(workRepository.findByIdForUpdate(15L)).thenReturn(Optional.of(work));

        assertThrows(BusinessConflictException.class, () -> handoverService.startHandover(15L));
        verify(handoverRepository, never()).save(any());
    }

    @Test
    void failHandover_wipesWholeDraftAndReturnsToNotStarted() {
        Work work = work(16L, "中途失败", 4);
        work.setHandoverStatus(HandoverService.STATUS_IN_PROGRESS);
        Handover handover = draft(16L);
        handover.setConditionStatus("完好");
        handover.setReceiver("张三"); // 已登记一半内容
        when(workRepository.findByIdForUpdate(16L)).thenReturn(Optional.of(work));
        when(handoverRepository.findByWorkId(16L)).thenReturn(Optional.of(handover));

        handoverService.failHandover(16L);

        assertEquals(HandoverService.STATUS_NOT_STARTED, work.getHandoverStatus());
        // 整单删除：不能一半件数留下、一半空着
        verify(handoverRepository).delete(handover);
    }

    @Test
    void failFromCompleted_isRejected() {
        Work work = work(17L, "已点交不能失败", 2);
        work.setHandoverStatus(HandoverService.STATUS_COMPLETED);
        when(workRepository.findByIdForUpdate(17L)).thenReturn(Optional.of(work));

        assertThrows(BusinessConflictException.class, () -> handoverService.failHandover(17L));
        verify(handoverRepository, never()).delete(any(Handover.class));
    }

    // ---------- 展陈看板 ----------

    @Test
    void board_onlyListsCompletedHandovers() {
        Work onWall = work(20L, "已点交作品", 2);
        onWall.setHandoverStatus(HandoverService.STATUS_COMPLETED);
        Handover completed = Handover.builder()
                .id(200L).workId(20L).status(HandoverService.STATUS_COMPLETED)
                .conditionStatus("完好").receiver("赵六").pieceCountSnapshot(2)
                .completedAt(LocalDateTime.now()).build();
        when(workRepository.findByHandoverStatus(HandoverService.STATUS_COMPLETED))
                .thenReturn(List.of(onWall));
        when(handoverRepository.findByWorkIdIn(List.of(20L))).thenReturn(List.of(completed));

        List<ExhibitionBoardItem> board = exhibitionService.getBoard();

        assertEquals(1, board.size());
        ExhibitionBoardItem item = board.get(0);
        assertEquals(20L, item.getWorkId());
        assertEquals(2, item.getPieceCount());
        assertEquals("完好", item.getConditionStatus());
        assertEquals("赵六", item.getReceiver());
    }

    @Test
    void board_skipsWorkWhoseHandoverRowIsMissing() {
        // 防御：作品标记已点交但点交单丢失（数据不一致）→ 不上墙
        Work inconsistent = work(21L, "状态不一致", 2);
        inconsistent.setHandoverStatus(HandoverService.STATUS_COMPLETED);
        when(workRepository.findByHandoverStatus(HandoverService.STATUS_COMPLETED))
                .thenReturn(List.of(inconsistent));
        when(handoverRepository.findByWorkIdIn(List.of(21L))).thenReturn(List.of());

        assertTrue(exhibitionService.getBoard().isEmpty());
    }

    // ---------- 参展凭证 ----------

    @Test
    void certificate_cannotBeIssuedBeforeHandoverCompleted() {
        Work work = work(22L, "未点交领凭证", 2);
        when(workRepository.findByIdForUpdate(22L)).thenReturn(Optional.of(work));

        BusinessConflictException ex = assertThrows(BusinessConflictException.class,
                () -> exhibitionService.issueCertificate(22L));
        assertTrue(ex.getMessage().contains("点交"));
        verify(workRepository, never()).save(any());

        // 点交中同样不能领
        work.setHandoverStatus(HandoverService.STATUS_IN_PROGRESS);
        assertThrows(BusinessConflictException.class, () -> exhibitionService.issueCertificate(22L));
        verify(workRepository, never()).save(any());
    }

    @Test
    void certificate_issuedAfterCompletion_andIdempotent() {
        Work work = work(23L, "已点交领凭证", 2);
        work.setHandoverStatus(HandoverService.STATUS_COMPLETED);
        when(workRepository.findByIdForUpdate(23L)).thenReturn(Optional.of(work));

        Work issued = exhibitionService.issueCertificate(23L);
        assertEquals(ExhibitionService.CERT_ISSUED, issued.getCertificateStatus());
        assertNotNull(issued.getCertificateNo());
        assertNotNull(issued.getCertificateIssuedAt());

        // 重复领取幂等：凭证号不变
        Work again = exhibitionService.issueCertificate(23L);
        assertEquals(issued.getCertificateNo(), again.getCertificateNo());
    }

    // ---------- 已点交后改件数 ----------

    @Test
    void pieceCountChangeAfterCompletion_pullsWallAndCertificate() {
        Work work = work(24L, "改件数", 3);
        work.setHandoverStatus(HandoverService.STATUS_COMPLETED);
        work.setCertificateStatus(ExhibitionService.CERT_ISSUED);
        work.setCertificateNo("CZ-24-1");
        Handover completed = Handover.builder()
                .id(240L).workId(24L).status(HandoverService.STATUS_COMPLETED)
                .conditionStatus("完好").receiver("张三").pieceCountSnapshot(3)
                .completedAt(LocalDateTime.now()).build();
        when(workRepository.findByIdForUpdate(24L)).thenReturn(Optional.of(work));
        when(handoverRepository.findByWorkId(24L)).thenReturn(Optional.of(completed));

        workService.updateWork(24L, Work.builder().pieceCount(5).status(null).build());

        assertEquals(5, work.getPieceCount());
        assertEquals(HandoverService.STATUS_NOT_STARTED, work.getHandoverStatus(), "必须退回未点交");
        verify(handoverRepository).delete(completed);
        assertEquals(ExhibitionService.CERT_REVOKED, work.getCertificateStatus(), "凭证必须先撤下来");
        assertNotNull(work.getCertificateRevokedAt());

        // 重新点交前：看板查不到它（状态已非已点交），凭证也领不出来
        assertThrows(BusinessConflictException.class, () -> exhibitionService.issueCertificate(24L));
    }

    @Test
    void pieceCountUnchanged_keepsHandoverAndCertificate() {
        Work work = work(25L, "件数没变", 3);
        work.setHandoverStatus(HandoverService.STATUS_COMPLETED);
        work.setCertificateStatus(ExhibitionService.CERT_ISSUED);
        when(workRepository.findByIdForUpdate(25L)).thenReturn(Optional.of(work));

        workService.updateWork(25L, Work.builder().pieceCount(3).workName("改个名").status(null).build());

        assertEquals("改个名", work.getWorkName());
        assertEquals(HandoverService.STATUS_COMPLETED, work.getHandoverStatus(), "件数没变不得误伤点交");
        assertEquals(ExhibitionService.CERT_ISSUED, work.getCertificateStatus());
        verify(handoverRepository, never()).delete(any(Handover.class));
    }

    @Test
    void pieceCountChangeWhileInProgress_keepsDraft() {
        // 点交中改件数：点交单还没成立，不涉及展墙与凭证，草稿继续
        Work work = work(26L, "点交中改件数", 3);
        work.setHandoverStatus(HandoverService.STATUS_IN_PROGRESS);
        when(workRepository.findByIdForUpdate(26L)).thenReturn(Optional.of(work));

        workService.updateWork(26L, Work.builder().pieceCount(4).status(null).build());

        assertEquals(4, work.getPieceCount());
        assertEquals(HandoverService.STATUS_IN_PROGRESS, work.getHandoverStatus());
        verify(handoverRepository, never()).delete(any(Handover.class));
    }

    @Test
    void rehandoverAfterInvalidation_goesBackOnWall() {
        Work work = work(27L, "重新点交", 3);
        work.setHandoverStatus(HandoverService.STATUS_COMPLETED);
        Handover completed = draft(27L);
        completed.setStatus(HandoverService.STATUS_COMPLETED);
        when(workRepository.findByIdForUpdate(27L)).thenReturn(Optional.of(work));
        when(handoverRepository.findByWorkId(27L)).thenReturn(Optional.of(completed));

        // 件数 3 → 6：点交作废
        workService.updateWork(27L, Work.builder().pieceCount(6).status(null).build());
        assertEquals(HandoverService.STATUS_NOT_STARTED, work.getHandoverStatus());

        // 重新走完整点交流程：未点交 → 点交中 → 已点交
        when(handoverRepository.findByWorkId(27L)).thenReturn(Optional.empty());
        Handover restarted = handoverService.startHandover(27L);
        when(handoverRepository.findByWorkId(27L)).thenReturn(Optional.of(restarted));
        handoverService.completeHandover(27L, "完好", "李四");

        assertEquals(HandoverService.STATUS_COMPLETED, work.getHandoverStatus());
        assertEquals(6, restarted.getPieceCountSnapshot(), "新点交单必须钉住新件数");

        // 重新点交完成后才能再领凭证
        Work issued = exhibitionService.issueCertificate(27L);
        assertEquals(ExhibitionService.CERT_ISSUED, issued.getCertificateStatus());
    }

    @Test
    void invalidPieceCount_isRejected() {
        assertThrows(BusinessValidationException.class,
                () -> workService.createWork(work(28L, "零件", 0)));
        Work work = work(29L, "负件数", 2);
        when(workRepository.findByIdForUpdate(29L)).thenReturn(Optional.of(work));
        assertThrows(BusinessValidationException.class,
                () -> workService.updateWork(29L, Work.builder().pieceCount(-1).status(null).build()));
    }

}
