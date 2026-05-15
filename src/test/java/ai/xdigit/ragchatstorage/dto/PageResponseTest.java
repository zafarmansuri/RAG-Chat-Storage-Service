package ai.xdigit.ragchatstorage.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void fromMapsSinglePageCorrectly() {
        List<String> items = List.of("a", "b");
        PageRequest pr = PageRequest.of(0, 10);
        PageImpl<String> page = new PageImpl<>(items, pr, 2);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void fromSetsHasNextTrueWhenMorePagesExist() {
        List<String> items = List.of("a");
        PageRequest pr = PageRequest.of(0, 1);
        PageImpl<String> page = new PageImpl<>(items, pr, 3);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.hasNext()).isTrue();
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    void fromHandlesEmptyPage() {
        PageRequest pr = PageRequest.of(5, 10);
        PageImpl<String> page = new PageImpl<>(List.of(), pr, 0);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.hasNext()).isFalse();
    }
}
