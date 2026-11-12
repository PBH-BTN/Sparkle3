package com.ghostchu.btn.sparkle.service.github;

import com.ghostchu.btn.sparkle.service.btnability.IPAllowListRuleProvider;
import com.ghostchu.btn.sparkle.service.btnability.IPDenyListRuleProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GithubRuleSourceCatalogTest {
    @Test
    void combinesSelectedSourcesInCatalogOrderAndDeduplicatesRepeatedSelection() {
        IPDenyListRuleProvider untrusted = mock(IPDenyListRuleProvider.class);
        when(untrusted.getContent()).thenReturn("# untrusted\n1.1.1.1");
        IPDenyListRuleProvider overdownload = mock(IPDenyListRuleProvider.class);
        when(overdownload.getContent()).thenReturn("# overdownload\n2.2.2.2");

        GithubRuleSourceCatalog catalog = new GithubRuleSourceCatalog(
                Map.of(
                        "analyseRuleUnTrustVoteServiceImpl", untrusted,
                        "analyseRuleOverDownloadServiceImpl", overdownload
                ),
                Map.<String, IPAllowListRuleProvider>of()
        );

        String content = catalog.combineContent(List.of(
                "overdownload",
                "untrusted-vote",
                "overdownload"
        ));

        assertEquals(
                "# untrusted\n1.1.1.1\n\n# overdownload\n2.2.2.2\n",
                content
        );
        assertTrue(catalog.isAvailable("untrusted-vote"));
        assertFalse(catalog.isAvailable("btnbypass-userapps"));
    }
}
