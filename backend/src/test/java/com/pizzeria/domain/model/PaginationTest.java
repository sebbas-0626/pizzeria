package com.pizzeria.domain.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginationTest {

    @Test
    void pageRequestRequiresNonnegativePageAndPositiveSize() {
        PageRequest pageRequest = new PageRequest(0, 20);
        assertThat(pageRequest.page()).isZero();
        assertThat(pageRequest.size()).isEqualTo(20);
        assertThatThrownBy(() -> new PageRequest(-1, 20))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageRequest(0, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pageResultCalculatesTotalPagesAndPreservesEmptyPageMetadata() {
        PageResult<String> partial = new PageResult<>(List.of("item"), 1, 3, 4);
        PageResult<String> empty = new PageResult<>(List.of(), 0, 3, 0);

        assertThat(partial.totalPages()).isEqualTo(2);
        assertThat(empty.totalPages()).isZero();
    }
}
