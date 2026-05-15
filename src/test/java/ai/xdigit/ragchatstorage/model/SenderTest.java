package ai.xdigit.ragchatstorage.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SenderTest {

    @Test
    void enumHasExactlyTwoValues() {
        assertThat(Sender.values()).containsExactlyInAnyOrder(Sender.USER, Sender.ASSISTANT);
    }

    @Test
    void valueOfWorksForBothLiterals() {
        assertThat(Sender.valueOf("USER")).isEqualTo(Sender.USER);
        assertThat(Sender.valueOf("ASSISTANT")).isEqualTo(Sender.ASSISTANT);
    }
}
