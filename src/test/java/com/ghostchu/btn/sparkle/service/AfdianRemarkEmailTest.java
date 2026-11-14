package com.ghostchu.btn.sparkle.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AfdianRemarkEmailTest {
    @Test
    void acceptsWholeMailboxAndNormalizesCaseWithoutRemovingPlusTags() {
        assertEquals("buyer+btn@example.test", AfdianRemarkEmail.extract("  Buyer+BTN@Example.TEST  "));
    }

    @Test
    void extractsMailboxFromChineseProseDisplayNamesAndMultilineRemarks() {
        assertEquals("buyer@example.test", AfdianRemarkEmail.extract("我的邮箱是buyer@example.test，请绑定，谢谢！"));
        assertEquals("buyer@example.test", AfdianRemarkEmail.extract("请绑定buyer@example.test"));
        assertEquals("buyer@example.test", AfdianRemarkEmail.extract("BTN: Buyer <buyer@example.test>"));
        assertEquals("buyer@example.test", AfdianRemarkEmail.extract("绑定账号\nbuyer@example.test. 谢谢"));
        assertEquals("buyer@example.test", AfdianRemarkEmail.extract("buyer@example.test / BUYER@EXAMPLE.TEST"));
    }

    @Test
    void rejectsAmbiguousMalformedOrInternalMailboxes() {
        assertNull(AfdianRemarkEmail.extract("buyer@example.test 或 other@example.test"));
        assertNull(AfdianRemarkEmail.extract("buyer..name@example.test"));
        assertNull(AfdianRemarkEmail.extract("no email here"));
        assertNull(AfdianRemarkEmail.extract("system@sparkle.system"));
        assertNull(AfdianRemarkEmail.extract("a".repeat(17000) + "buyer@example.test"));
        assertNull(AfdianRemarkEmail.extract(null));
    }
}
