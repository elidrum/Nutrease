import pytest

from nutrease.models.user import _validate_password


def test_validate_password_ok():
    _validate_password("Password1")


def test_validate_password_requires_number():
    with pytest.raises(ValueError):
        _validate_password("Password")


def test_validate_password_requires_uppercase():
    with pytest.raises(ValueError):
        _validate_password("password1")