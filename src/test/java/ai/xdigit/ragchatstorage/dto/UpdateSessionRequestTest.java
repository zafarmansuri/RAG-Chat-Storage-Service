package ai.xdigit.ragchatstorage.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateSessionRequestTest {

    @Test
    void isEmptyReturnsTrueWhenBothFieldsNull() {
        assertThat(new UpdateSessionRequest(null, null).isEmpty()).isTrue();
    }

    @Test
    void isEmptyReturnsFalseWhenTitlePresent() {
        assertThat(new UpdateSessionRequest("New Title", null).isEmpty()).isFalse();
    }

    @Test
    void isEmptyReturnsFalseWhenFavoritePresent() {
        assertThat(new UpdateSessionRequest(null, true).isEmpty()).isFalse();
    }

    @Test
    void isEmptyReturnsFalseWhenBothPresent() {
        assertThat(new UpdateSessionRequest("T", false).isEmpty()).isFalse();
    }
}
