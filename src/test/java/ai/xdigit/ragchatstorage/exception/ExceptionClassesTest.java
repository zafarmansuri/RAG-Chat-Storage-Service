package ai.xdigit.ragchatstorage.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionClassesTest {

    @Test
    void badRequestExceptionCarriesMessage() {
        BadRequestException ex = new BadRequestException("bad input");
        assertThat(ex.getMessage()).isEqualTo("bad input");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    void resourceNotFoundExceptionCarriesMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("session not found");
        assertThat(ex.getMessage()).isEqualTo("session not found");
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
