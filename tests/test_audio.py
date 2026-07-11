from backend import audio

SAMPLE_HEADPHONES = """
Audio:
    Devices:
        MacBook Pro Speakers:
          Default Output Device: Spam
        AirPods Pro:
          Default Output Device: Yes
          Transport: Bluetooth
"""

SAMPLE_SPEAKERS = """
Audio:
    Devices:
        MacBook Pro Speakers:
          Default Output Device: Yes
        External Microphone:
          Default Input Device: Yes
"""


def test_looks_like_earphone_true():
    assert audio._looks_like_earphone("AirPods Pro")
    assert audio._looks_like_earphone("Bose QuietComfort Headphones")
    assert audio._looks_like_earphone("USB-C Earbuds")


def test_looks_like_earphone_false():
    assert not audio._looks_like_earphone("MacBook Pro Speakers")
    assert not audio._looks_like_earphone("Studio Display Speakers")


def test_parse_default_output_headphones():
    assert audio._parse_default_output(SAMPLE_HEADPHONES) == "AirPods Pro"


def test_parse_default_output_speakers():
    assert audio._parse_default_output(SAMPLE_SPEAKERS) == "MacBook Pro Speakers"
